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

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openmrs.Concept;
import org.openmrs.GlobalProperty;
import org.openmrs.Location;
import org.openmrs.OpenmrsObject;
import org.openmrs.api.APIException;
import org.openmrs.api.context.Context;
import org.openmrs.module.initializer.Domain;
import org.openmrs.module.initializer.api.CsvFailingLines;
import org.openmrs.module.initializer.api.queues.QueueCsvParser;
import org.openmrs.module.initializer.api.queues.QueueLineProcessor;
import org.openmrs.module.metadataexport.export.ExportContext;
import org.openmrs.module.metadataexport.export.ExportLine;
import org.openmrs.module.queue.QueueModuleConstants;
import org.openmrs.module.queue.api.QueueService;
import org.openmrs.module.queue.api.search.QueueSearchCriteria;
import org.openmrs.module.queue.model.Queue;
import org.openmrs.test.jupiter.BaseModuleContextSensitiveTest;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueueDomainExporterIntegrationTest extends BaseModuleContextSensitiveTest {
	
	private static final String LIVE_UUID = "c1d8a345-3f10-11e4-adec-0800271c1b75";
	
	private static final String BARE_UUID = "8c2f6a1e-5b7d-4e3a-9f10-2d4c6e8a0b1c";
	
	private static final String RETIRED_UUID = "439559c2-a3a4-4a25-b4b2-1a0299e287ee";
	
	private static final String UNKNOWN_UUID = "7e3f4d5a-3f10-11e4-adec-0800271c1b75";
	
	private final QueueDomainExporter exporter = new QueueDomainExporter();
	
	private Location location;
	
	private Concept serviceSet;
	
	private Concept service;
	
	private Concept statuses;
	
	private Concept priorities;
	
	/**
	 * Seeds a fully populated live queue, a live queue with only the mandatory columns, and a retired
	 * queue.
	 */
	@BeforeEach
	void seedQueues() {
		location = Context.getLocationService().getLocation(1);
		// The queue module only accepts a service that is a member of the configured service concept set,
		// so point the global property at set 23 and use one of its members as the service.
		serviceSet = Context.getConceptService().getConcept(23);
		Context.getAdministrationService()
		        .saveGlobalProperty(new GlobalProperty(QueueModuleConstants.QUEUE_SERVICE, serviceSet.getUuid()));
		// Core runs validators with the session in manual flush mode, so the property must already be in
		// the database when the queue validator looks it up (the lookup is a query, not a by-id get, so
		// the session cache does not help).
		Context.flushSession();
		service = Context.getConceptService().getConcept(18);
		statuses = Context.getConceptService().getConcept(4);
		priorities = Context.getConceptService().getConcept(5);
		
		Queue live = mandatoryQueue(LIVE_UUID, "Triage Queue");
		live.setDescription("Queue with custom statuses");
		live.setStatusConceptSet(statuses);
		live.setPriorityConceptSet(priorities);
		queueService().createQueue(live);
		
		queueService().createQueue(mandatoryQueue(BARE_UUID, "Consultation Queue"));
		
		Queue retired = mandatoryQueue(RETIRED_UUID, "Old Queue");
		queueService().createQueue(retired);
		queueService().retireQueue(retired, "No longer needed");
		Context.flushSession();
	}
	
	@Test
	void getAllInstances_excludesRetiredQueues() {
		Collection<Queue> instances = exporter.getAllInstances();
		
		assertEquals(new HashSet<>(Arrays.asList(LIVE_UUID, BARE_UUID)), uuidsOf(instances));
	}
	
	@Test
	void getInstancesByUuids_returnsALiveRow() {
		Collection<Queue> found = exporter.getInstancesByUuids(Collections.singletonList(LIVE_UUID));
		
		assertEquals(1, found.size());
		assertEquals(LIVE_UUID, found.iterator().next().getUuid());
	}
	
	@Test
	void getInstancesByUuids_reportsARetiredRowAsNotImportableRatherThanUnknown() {
		APIException e = assertThrows(APIException.class,
		    () -> exporter.getInstancesByUuids(Collections.singletonList(RETIRED_UUID)));
		
		assertTrue(e.getMessage().contains("retired"), "a retired row must not be reported as unknown");
		assertTrue(e.getMessage().contains(RETIRED_UUID));
		assertFalse(e.getMessage().contains("Unknown uuids"));
	}
	
	@Test
	void getInstancesByUuids_stillReportsUnknownUuids() {
		APIException e = assertThrows(APIException.class,
		    () -> exporter.getInstancesByUuids(Collections.singletonList(UNKNOWN_UUID)));
		
		assertTrue(e.getMessage().contains("Unknown uuids"));
		assertTrue(e.getMessage().contains(UNKNOWN_UUID));
	}
	
	@Test
	void getInstancesByUuids_reportsRetiredAndUnknownUuidsInOneMessage() {
		APIException e = assertThrows(APIException.class,
		    () -> exporter.getInstancesByUuids(Arrays.asList(LIVE_UUID, RETIRED_UUID, UNKNOWN_UUID)));
		
		assertTrue(e.getMessage().contains(RETIRED_UUID), "the retired uuid must be reported");
		assertTrue(e.getMessage().contains(UNKNOWN_UUID), "the unknown uuid must be reported in the same round");
		assertFalse(e.getMessage().contains(LIVE_UUID), "a resolvable uuid is not a problem");
	}
	
	@Test
	void getDependencies_pullsInTheReferencedObjectsAndTheServiceSetConfiguration() {
		// Reload through a fresh session so the references are the lazy proxies production sees.
		Context.flushSession();
		Context.clearSession();
		Queue live = reload(LIVE_UUID);
		
		Collection<? extends OpenmrsObject> dependencies = exporter.getDependencies(live);
		
		GlobalProperty gp = Context.getAdministrationService().getGlobalPropertyObject(QueueModuleConstants.QUEUE_SERVICE);
		assertEquals(new HashSet<>(Arrays.asList(location.getUuid(), service.getUuid(), statuses.getUuid(),
		    priorities.getUuid(), gp.getUuid(), serviceSet.getUuid())), uuidsOf(dependencies));
		
		ExportLine line = new ExportLine();
		new QueueLineExporter().writeLine(live, line);
		assertEquals(service.getUuid(), line.get("service"), "a proxied reference still yields its uuid");
		assertEquals(location.getUuid(), line.get("location"));
	}
	
	@Test
	void getDependencies_resolvesAServiceSetNamedByConceptName() {
		String setName = serviceSet.getName().getName();
		Context.getAdministrationService()
		        .saveGlobalProperty(new GlobalProperty(QueueModuleConstants.QUEUE_SERVICE, setName));
		Queue live = reload(LIVE_UUID);
		
		Collection<? extends OpenmrsObject> dependencies = exporter.getDependencies(live);
		
		assertTrue(uuidsOf(dependencies).contains(serviceSet.getUuid()),
		    "the set concept must be found by name, as the queue module itself resolves it");
	}
	
	@Test
	void getDependencies_fallsBackToTheDirectReferencesWhenTheServiceSetPropertyIsUnset() {
		Context.getAdministrationService().purgeGlobalProperty(
		    Context.getAdministrationService().getGlobalPropertyObject(QueueModuleConstants.QUEUE_SERVICE));
		Queue live = reload(LIVE_UUID);
		
		Collection<? extends OpenmrsObject> dependencies = exporter.getDependencies(live);
		
		assertEquals(
		    new HashSet<>(Arrays.asList(location.getUuid(), service.getUuid(), statuses.getUuid(), priorities.getUuid())),
		    uuidsOf(dependencies), "with no property to export, only the row's own references remain");
	}
	
	@Test
	void export_thenReimportOntoAFreshTarget(@TempDir File outDir) throws Exception {
		exporter.export(exporter.getAllInstances(), new ExportContext(outDir));
		purgeAllQueues();
		assertTrue(allRows().isEmpty(), "the target must start without the queues");
		
		CsvFailingLines failed = replayThroughInitializer(outDir);
		
		assertTrue(failed.getFailingLines().isEmpty(), describe(failed));
		Queue live = reload(LIVE_UUID);
		assertEquals("Triage Queue", live.getName());
		assertEquals("Queue with custom statuses", live.getDescription());
		assertEquals(location.getUuid(), live.getLocation().getUuid());
		assertEquals(service.getUuid(), live.getService().getUuid());
		assertEquals(statuses.getUuid(), live.getStatusConceptSet().getUuid());
		assertEquals(priorities.getUuid(), live.getPriorityConceptSet().getUuid());
		assertFalse(live.getRetired());
		Queue bare = reload(BARE_UUID);
		assertEquals("Consultation Queue", bare.getName());
		assertNull(bare.getDescription(), "a blank cell under a shared header must import as null");
		assertNull(bare.getStatusConceptSet());
		assertNull(bare.getPriorityConceptSet());
		assertEquals(service.getUuid(), bare.getService().getUuid());
		assertEquals(2, allRows().size(), "the retired queue is not in the file, so it must not reappear");
	}
	
	@Test
	void export_thenReimportOntoATargetThatAlreadyHasTheQueues(@TempDir File outDir) throws Exception {
		exporter.export(exporter.getAllInstances(), new ExportContext(outDir));
		
		CsvFailingLines failed = replayThroughInitializer(outDir);
		
		assertTrue(failed.getFailingLines().isEmpty(), describe(failed));
		assertEquals(3, allRows().size(), "existing rows are matched by uuid, not duplicated");
		assertFalse(reload(LIVE_UUID).getRetired());
		assertFalse(reload(BARE_UUID).getRetired());
		Queue retired = allRows().stream().filter(q -> RETIRED_UUID.equals(q.getUuid())).findFirst()
		        .orElseThrow(() -> new AssertionError("retired queue " + RETIRED_UUID + " disappeared"));
		assertTrue(retired.getRetired(), "the retired queue is not in the file, so the import must leave it alone");
		assertEquals("Old Queue", retired.getName());
	}
	
	private Queue mandatoryQueue(String uuid, String name) {
		Queue queue = new Queue();
		queue.setUuid(uuid);
		queue.setName(name);
		queue.setLocation(location);
		queue.setService(service);
		return queue;
	}
	
	private static Set<String> uuidsOf(Collection<? extends OpenmrsObject> objects) {
		return objects.stream().map(OpenmrsObject::getUuid).collect(Collectors.toSet());
	}
	
	private static Queue reload(String uuid) {
		return queueService().getQueueByUuid(uuid)
		        .orElseThrow(() -> new AssertionError("live queue " + uuid + " is not on the target"));
	}
	
	private static List<Queue> allRows() {
		QueueSearchCriteria all = new QueueSearchCriteria();
		all.setIncludeRetired(true);
		return queueService().getQueues(all);
	}
	
	private static void purgeAllQueues() {
		SessionFactory sessionFactory = Context.getRegisteredComponent("sessionFactory", SessionFactory.class);
		for (Queue queue : allRows()) {
			sessionFactory.getCurrentSession().delete(queue);
		}
		sessionFactory.getCurrentSession().flush();
	}
	
	/**
	 * Feeds the exported file back through Iniz's own parser, the only thing that shows the file is
	 * loadable rather than merely well-shaped.
	 */
	private static CsvFailingLines replayThroughInitializer(File outDir) throws Exception {
		File csv = outDir.toPath().resolve(Paths.get("configuration", Domain.QUEUES.getName(), "queues.csv")).toFile();
		assertTrue(csv.exists(), "expected " + csv);
		QueueCsvParser parser = new QueueCsvParser(queueService(),
		        new QueueLineProcessor(Context.getConceptService(), Context.getLocationService()));
		try (InputStream in = new FileInputStream(csv)) {
			parser.setInputStream(in);
			List<String[]> lines = parser.getLines();
			assertEquals(2, lines.size(), "only the two live queues must be in the file");
			return parser.process(lines);
		}
	}
	
	private static String describe(CsvFailingLines failed) {
		return failed.getErrorDetails().stream()
		        .map(d -> d.getCsvLine().prettyPrint() + " -> " + ExceptionUtils.getRootCauseMessage(d.getException()))
		        .collect(Collectors.joining("\n", "Iniz rejected exported lines:\n", ""));
	}
	
	private static QueueService queueService() {
		return Context.getService(QueueService.class);
	}
}
