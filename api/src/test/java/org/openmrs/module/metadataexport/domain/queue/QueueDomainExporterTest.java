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

import org.junit.jupiter.api.Test;
import org.openmrs.Concept;
import org.openmrs.Location;
import org.openmrs.OpenmrsObject;
import org.openmrs.module.initializer.Domain;
import org.openmrs.module.queue.model.Queue;
import org.openmrs.module.queue.model.QueueRoom;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueueDomainExporterTest {
	
	private final QueueDomainExporter exporter = new QueueDomainExporter();
	
	@Test
	void ownsTheQueuesDomain() {
		assertEquals(Domain.QUEUES, exporter.getDomain());
	}
	
	@Test
	void handlesOnlyQueues() {
		assertTrue(exporter.handles(new Queue()));
		assertFalse(exporter.handles(new QueueRoom()));
		assertFalse(exporter.handles(new Location()));
	}
	
	@Test
	void directReferences_areTheLocationAndConcepts() {
		Queue queue = new Queue();
		Location location = new Location();
		Concept service = new Concept();
		Concept statuses = new Concept();
		Concept priorities = new Concept();
		queue.setLocation(location);
		queue.setService(service);
		queue.setStatusConceptSet(statuses);
		queue.setPriorityConceptSet(priorities);
		
		List<OpenmrsObject> references = QueueDomainExporter.directReferences(queue);
		
		assertEquals(4, references.size());
		assertTrue(references.contains(location));
		assertTrue(references.contains(service));
		assertTrue(references.contains(statuses));
		assertTrue(references.contains(priorities));
	}
	
	@Test
	void directReferences_skipAbsentOptionalConceptSets() {
		Queue queue = new Queue();
		Location location = new Location();
		Concept service = new Concept();
		queue.setLocation(location);
		queue.setService(service);
		
		List<OpenmrsObject> references = QueueDomainExporter.directReferences(queue);
		
		assertEquals(2, references.size());
		assertTrue(references.contains(location));
		assertTrue(references.contains(service));
	}
	
	@Test
	void directReferences_areEmptyForAnEmptyQueue() {
		assertTrue(QueueDomainExporter.directReferences(new Queue()).isEmpty());
	}
}
