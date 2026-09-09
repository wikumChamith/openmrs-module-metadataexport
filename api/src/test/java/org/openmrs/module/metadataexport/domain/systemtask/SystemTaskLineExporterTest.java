/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.metadataexport.domain.systemtask;

import org.junit.jupiter.api.Test;
import org.openmrs.module.metadataexport.export.ExportLine;
import org.openmrs.module.tasks.Priority;
import org.openmrs.module.tasks.SystemTask;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SystemTaskLineExporterTest {
	
	@Test
	void exportsAllColumnsOfALiveTask() {
		SystemTask task = new SystemTask();
		task.setUuid("550e8400-e29b-41d4-a716-446655440001");
		task.setName("vital-check");
		task.setTitle("Daily Vital Check");
		task.setDescription("Check patient vitals every day");
		task.setRationale("Routine monitoring required");
		task.setPriority(Priority.HIGH);
		
		ExportLine line = new ExportLine();
		new SystemTaskLineExporter().writeLine(task, line);
		
		assertEquals("550e8400-e29b-41d4-a716-446655440001", line.get("uuid"));
		assertEquals("vital-check", line.get("name"));
		assertEquals("Daily Vital Check", line.get("title"));
		assertEquals("Check patient vitals every day", line.get("description"));
		assertEquals("Routine monitoring required", line.get("rationale"));
		assertEquals("HIGH", line.get("priority"), "priority is written as the enum constant Initializer parses");
		assertNull(line.get("void/retire"));
	}
	
	@Test
	void omitsOptionalColumnsWhenUnset() {
		SystemTask task = new SystemTask();
		task.setUuid("550e8400-e29b-41d4-a716-446655440002");
		task.setName("follow-up");
		task.setTitle("Follow-up Appointment");
		
		ExportLine line = new ExportLine();
		new SystemTaskLineExporter().writeLine(task, line);
		
		assertEquals("follow-up", line.get("name"));
		assertEquals("Follow-up Appointment", line.get("title"));
		assertNull(line.get("description"), "empty description is not written as a column");
		assertNull(line.get("rationale"), "empty rationale is not written as a column");
		assertNull(line.get("priority"), "unset priority is not written as a column");
		assertNull(line.get("default assignee role"), "no assignee means no assignee column");
	}
	
	@Test
	void noAssigneeDoesNotTouchTheProviderService() {
		SystemTask task = new SystemTask();
		task.setUuid("550e8400-e29b-41d4-a716-446655440003");
		task.setName("lab-review");
		task.setTitle("Lab Order Review");
		task.setPriority(Priority.LOW);
		task.setDefaultAssigneeProviderRoleId(null);
		
		ExportLine line = new ExportLine();
		new SystemTaskLineExporter().writeLine(task, line);
		
		assertEquals("LOW", line.get("priority"));
		assertNull(line.get("default assignee role"));
	}
	
	@Test
	void retiredTaskEmitsFullRowPlusFlag() {
		SystemTask task = new SystemTask();
		task.setUuid("439559c2-a3a4-4a25-b4b2-1a0299e287ee");
		task.setName("discontinued");
		task.setTitle("Discontinued Task");
		task.setPriority(Priority.MEDIUM);
		task.setRetired(true);
		
		ExportLine line = new ExportLine();
		new SystemTaskLineExporter().writeLine(task, line);
		
		assertEquals("439559c2-a3a4-4a25-b4b2-1a0299e287ee", line.get("uuid"));
		assertEquals("true", line.get("void/retire"));
		assertEquals("discontinued", line.get("name"),
		    "Iniz bootstraps retired rows with unknown uuids and name is NOT NULL, so they carry the full row");
		assertEquals("Discontinued Task", line.get("title"), "title is NOT NULL on the target too");
		assertEquals("MEDIUM", line.get("priority"));
	}
}
