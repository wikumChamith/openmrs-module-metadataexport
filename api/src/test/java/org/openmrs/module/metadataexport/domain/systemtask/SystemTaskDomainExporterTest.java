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
import org.openmrs.ProviderRole;
import org.openmrs.module.initializer.Domain;
import org.openmrs.module.tasks.SystemTask;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemTaskDomainExporterTest {
	
	private final SystemTaskDomainExporter exporter = new SystemTaskDomainExporter();
	
	@Test
	void ownsTheSystemTasksDomain() {
		assertEquals(Domain.SYSTEM_TASKS, exporter.getDomain());
	}
	
	@Test
	void writesASingleSystemTasksFile() {
		assertEquals("systemTasks.csv", exporter.fileName());
	}
	
	@Test
	void handlesOnlySystemTasks() {
		assertTrue(exporter.handles(new SystemTask()));
		assertFalse(exporter.handles(new ProviderRole()));
	}
	
	@Test
	void noAssigneeMeansNoDependencies() {
		SystemTask task = new SystemTask();
		task.setName("vital-check");
		task.setTitle("Daily Vital Check");
		
		assertTrue(exporter.getDependencies(task).isEmpty());
	}
}
