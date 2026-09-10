/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.metadataexport.domain.queue;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.openmrs.Concept;
import org.openmrs.GlobalProperty;
import org.openmrs.OpenmrsObject;
import org.openmrs.annotation.OpenmrsProfile;
import org.openmrs.api.APIException;
import org.openmrs.api.ConceptService;
import org.openmrs.api.context.Context;
import org.openmrs.module.initializer.Domain;
import org.openmrs.module.metadataexport.export.BaseLineExporter;
import org.openmrs.module.metadataexport.export.CsvDomainExporter;
import org.openmrs.module.queue.QueueModuleConstants;
import org.openmrs.module.queue.api.QueueService;
import org.openmrs.module.queue.api.search.QueueSearchCriteria;
import org.openmrs.module.queue.model.Queue;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@OpenmrsProfile(modules = "queue:3.0.0 - 9.*")
public class QueueDomainExporter extends CsvDomainExporter<Queue> {
	
	@Override
	protected List<BaseLineExporter<Queue>> chain() {
		return Collections.singletonList(new QueueLineExporter());
	}
	
	@Override
	protected String fileName() {
		return "queues.csv";
	}
	
	@Override
	public Domain getDomain() {
		return Domain.QUEUES;
	}
	
	@Override
	public boolean handles(OpenmrsObject instance) {
		return instance instanceof Queue;
	}
	
	@Override
	public Collection<Queue> getAllInstances() {
		// Retired queues are dropped on purpose. Initializer's parser bootstraps a row through
		// QueueService.getQueueByUuid, whose lookup excludes retired queues, so a retired row can never be
		// matched on the target: where the target already has it, the re-import tries to insert a second
		// row and fails on the uuid constraint, and Initializer's error handling then abandons the rest of
		// the file (as of Initializer 2.12 on core 2.8: the failed identity insert leaves an id-less entity
		// in the session, and CsvParser's catch block evicts it, which throws out of the processing loop).
		List<Queue> live = new ArrayList<>();
		for (Queue queue : allQueues()) {
			if (BooleanUtils.isTrue(queue.getRetired())) {
				log.warn(
				    "Queues: skipping retired queue {} ({}); Initializer looks queues up through the queue"
				            + " module's getQueueByUuid, which excludes retired rows, so a retired queue cannot be imported",
				    queue.getUuid(), queue.getName());
			} else {
				live.add(queue);
			}
		}
		return live;
	}
	
	/**
	 * Same as the default, but a uuid that exists only as a retired queue is reported as not importable
	 * rather than unknown, since {@link #getAllInstances()} hides retired rows.
	 */
	@Override
	public Collection<Queue> getInstancesByUuids(Collection<String> uuids) {
		Set<String> wanted = new HashSet<>(uuids);
		List<Queue> found = new ArrayList<>();
		for (Queue queue : getAllInstances()) {
			if (wanted.remove(queue.getUuid())) {
				found.add(queue);
			}
		}
		if (!wanted.isEmpty()) {
			List<String> retired = allQueues().stream().map(Queue::getUuid).filter(wanted::contains)
			        .collect(Collectors.toList());
			wanted.removeAll(retired);
			List<String> problems = new ArrayList<>();
			if (!retired.isEmpty()) {
				problems.add("Queues exist but are retired, and Initializer cannot import a retired queue"
				        + " (unretire them on this server or remove them from the package): " + retired);
			}
			if (!wanted.isEmpty()) {
				problems.add("Unknown uuids in domain " + getDomain() + ": " + wanted);
			}
			throw new APIException(String.join("; ", problems));
		}
		return found;
	}
	
	/**
	 * The location and concepts the row refers to, plus what the queue module's validator needs on the
	 * target: the {@code queue.serviceConceptSetName} global property and the concept set it names,
	 * without which every imported queue is rejected.
	 */
	@Override
	public Collection<? extends OpenmrsObject> getDependencies(Queue instance) {
		List<OpenmrsObject> dependencies = new ArrayList<>(directReferences(instance));
		GlobalProperty serviceSetProperty = Context.getAdministrationService()
		        .getGlobalPropertyObject(QueueModuleConstants.QUEUE_SERVICE);
		String serviceSetReference = serviceSetProperty == null ? null : serviceSetProperty.getPropertyValue();
		if (StringUtils.isBlank(serviceSetReference)) {
			log.warn("Queues: global property {} is not set on this server, so queue {} will be rejected on import"
			        + " until the importing server sets it",
			    QueueModuleConstants.QUEUE_SERVICE, instance.getUuid());
			return dependencies;
		}
		dependencies.add(serviceSetProperty);
		Concept serviceSet = resolveConcept(serviceSetReference);
		if (serviceSet == null) {
			log.warn(
			    "Queues: global property {} names concept '{}', which does not exist on this server, so the"
			            + " service concept set cannot be exported alongside queue {}",
			    QueueModuleConstants.QUEUE_SERVICE, serviceSetReference, instance.getUuid());
		} else {
			dependencies.add(serviceSet);
		}
		return dependencies;
	}
	
	/** The objects a queue row itself refers to: its location, service and optional concept sets. */
	static List<OpenmrsObject> directReferences(Queue instance) {
		List<OpenmrsObject> references = new ArrayList<>();
		if (instance.getLocation() != null) {
			references.add(instance.getLocation());
		}
		if (instance.getService() != null) {
			references.add(instance.getService());
		}
		if (instance.getStatusConceptSet() != null) {
			references.add(instance.getStatusConceptSet());
		}
		if (instance.getPriorityConceptSet() != null) {
			references.add(instance.getPriorityConceptSet());
		}
		return references;
	}
	
	/**
	 * Mirrors how the queue module resolves the property: by uuid, then "source:code" mapping, then
	 * name.
	 */
	private static Concept resolveConcept(String reference) {
		ConceptService conceptService = Context.getConceptService();
		Concept concept = conceptService.getConceptByUuid(reference);
		if (concept != null) {
			return concept;
		}
		int colon = reference.indexOf(':');
		if (colon > 0 && colon < reference.length() - 1) {
			concept = conceptService.getConceptByMapping(reference.substring(colon + 1), reference.substring(0, colon));
			if (concept != null) {
				return concept;
			}
		}
		return conceptService.getConceptByName(reference);
	}
	
	/** Every queue on the server, retired ones included. */
	private static List<Queue> allQueues() {
		QueueSearchCriteria criteria = new QueueSearchCriteria();
		criteria.setIncludeRetired(true);
		return Context.getService(QueueService.class).getQueues(criteria);
	}
}
