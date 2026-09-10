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
import org.openmrs.module.metadataexport.export.ExportLine;
import org.openmrs.module.queue.model.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class QueueLineExporterTest {
	
	private static final String QUEUE_UUID = "2a0e0eee-6888-11ee-ab8d-0242ac120002";
	
	private static final String SERVICE_UUID = "67b910bd-298c-4ecf-a632-661ae2f446ab";
	
	private static final String STATUSES_UUID = "1d2a73ca-20aa-4218-b4d2-043024a9156e";
	
	private static final String PRIORITIES_UUID = "24932838-60ca-44e8-840f-4184b368643c";
	
	private static final String LOCATION_UUID = "167ce20c-4785-4285-9119-d197268f7f4a";
	
	@Test
	void exportsAllColumnsOfALiveQueue() {
		Queue queue = triageQueue();
		
		ExportLine line = new ExportLine();
		new QueueLineExporter().writeLine(queue, line);
		
		assertEquals(QUEUE_UUID, line.get("uuid"));
		assertEquals("Triage Queue", line.get("name"));
		assertEquals("Queue with custom statuses", line.get("description"));
		assertEquals(SERVICE_UUID, line.get("service"));
		assertEquals(STATUSES_UUID, line.get("status concept set"));
		assertEquals(PRIORITIES_UUID, line.get("priority concept set"));
		assertEquals(LOCATION_UUID, line.get("location"));
		assertNull(line.get("void/retire"));
	}
	
	@Test
	void omitsAbsentOptionalColumns() {
		Queue queue = triageQueue();
		queue.setDescription(null);
		queue.setStatusConceptSet(null);
		queue.setPriorityConceptSet(null);
		
		ExportLine line = new ExportLine();
		new QueueLineExporter().writeLine(queue, line);
		
		assertEquals("Triage Queue", line.get("name"));
		assertEquals(SERVICE_UUID, line.get("service"));
		assertEquals(LOCATION_UUID, line.get("location"));
		assertNull(line.get("description"));
		assertNull(line.get("status concept set"));
		assertNull(line.get("priority concept set"));
	}
	
	@Test
	void corruptRowWithoutServiceOrLocationIsStillWrittenWithoutThoseColumns() {
		Queue queue = triageQueue();
		queue.setService(null);
		queue.setLocation(null);
		
		ExportLine line = new ExportLine();
		new QueueLineExporter().writeLine(queue, line);
		
		assertEquals("Triage Queue", line.get("name"), "the row is written (and warned about), not dropped");
		assertNull(line.get("service"));
		assertNull(line.get("location"));
	}
	
	@Test
	void retiredQueueEmitsUuidAndFlagOnly() {
		Queue queue = triageQueue();
		queue.setRetired(true);
		
		ExportLine line = new ExportLine();
		new QueueLineExporter().writeLine(queue, line);
		
		assertEquals(QUEUE_UUID, line.get("uuid"));
		assertEquals("true", line.get("void/retire"));
		assertNull(line.get("name"), "retired rows never reach the file; if one leaked it would be the bare default");
		assertNull(line.get("service"));
	}
	
	private static Queue triageQueue() {
		Queue queue = new Queue();
		queue.setUuid(QUEUE_UUID);
		queue.setName("Triage Queue");
		queue.setDescription("Queue with custom statuses");
		queue.setService(concept(SERVICE_UUID));
		queue.setStatusConceptSet(concept(STATUSES_UUID));
		queue.setPriorityConceptSet(concept(PRIORITIES_UUID));
		Location location = new Location();
		location.setUuid(LOCATION_UUID);
		queue.setLocation(location);
		return queue;
	}
	
	private static Concept concept(String uuid) {
		Concept concept = new Concept();
		concept.setUuid(uuid);
		return concept;
	}
}
