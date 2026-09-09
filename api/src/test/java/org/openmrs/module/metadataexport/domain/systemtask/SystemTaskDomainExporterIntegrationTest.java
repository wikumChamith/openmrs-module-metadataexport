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

import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openmrs.OpenmrsObject;
import org.openmrs.ProviderRole;
import org.openmrs.api.context.Context;
import org.openmrs.module.initializer.Domain;
import org.openmrs.module.initializer.api.CsvFailingLines;
import org.openmrs.module.initializer.api.systemtasks.SystemTasksCsvParser;
import org.openmrs.module.initializer.api.systemtasks.SystemTasksLineProcessor;
import org.openmrs.module.metadataexport.export.ExportContext;
import org.openmrs.module.metadataexport.export.ExportLine;
import org.openmrs.module.tasks.Priority;
import org.openmrs.module.tasks.SystemTask;
import org.openmrs.module.tasks.api.TasksService;
import org.openmrs.test.jupiter.BaseModuleContextSensitiveTest;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemTaskDomainExporterIntegrationTest extends BaseModuleContextSensitiveTest {
	
	private static final String ROLE_UUID = "6a9d7e9a-2f3b-4c1d-9e8f-1a2b3c4d5e6f";
	
	private static final String LIVE_UUID = "c1d8a345-3f10-11e4-adec-0800271c1b75";
	
	private static final String RETIRED_UUID = "439559c2-a3a4-4a25-b4b2-1a0299e287ee";
	
	private final SystemTaskDomainExporter exporter = new SystemTaskDomainExporter();
	
	private ProviderRole nurse;
	
	@BeforeEach
	void seedProviderRole() {
		nurse = new ProviderRole();
		nurse.setUuid(ROLE_UUID);
		nurse.setName("Nurse");
		nurse.setDescription("Ward nursing staff");
		SessionFactory sessionFactory = Context.getRegisteredComponent("sessionFactory", SessionFactory.class);
		sessionFactory.getCurrentSession().saveOrUpdate(nurse);
		sessionFactory.getCurrentSession().flush();
	}
	
	@Test
	void getDependencies_pullsInTheAssigneeProviderRole() {
		SystemTask task = taskAssignedTo(nurse.getProviderRoleId());
		
		Collection<? extends OpenmrsObject> dependencies = exporter.getDependencies(task);
		
		assertEquals(1, dependencies.size());
		assertEquals(ROLE_UUID, dependencies.iterator().next().getUuid());
	}
	
	@Test
	void getDependencies_ignoresAnUnknownAssignee() {
		SystemTask task = taskAssignedTo(Integer.MAX_VALUE);
		
		assertTrue(exporter.getDependencies(task).isEmpty());
	}
	
	@Test
	void lineExporter_writesTheAssigneeAsAUuid() {
		SystemTask task = taskAssignedTo(nurse.getProviderRoleId());
		
		ExportLine line = new ExportLine();
		new SystemTaskLineExporter().writeLine(task, line);
		
		assertEquals(ROLE_UUID, line.get("default assignee role"));
	}
	
	@Test
	void lineExporter_omitsAnUnknownAssignee() {
		SystemTask task = taskAssignedTo(Integer.MAX_VALUE);
		
		ExportLine line = new ExportLine();
		new SystemTaskLineExporter().writeLine(task, line);
		
		assertEquals("vital-check", line.get("name"));
		assertNull(line.get("default assignee role"));
	}
	
	@Test
	void getAllInstances_includesRetiredTasks() {
		seedOneLiveAndOneRetiredTask();
		
		Collection<SystemTask> instances = exporter.getAllInstances();
		
		assertEquals(2, instances.size());
		Set<String> uuids = instances.stream().map(SystemTask::getUuid).collect(Collectors.toSet());
		assertEquals(new HashSet<>(Arrays.asList(LIVE_UUID, RETIRED_UUID)), uuids,
		    "retired tasks are exported too, so their retirement replays on the target");
		SystemTask retired = instances.stream().filter(t -> RETIRED_UUID.equals(t.getUuid())).findFirst().get();
		assertTrue(retired.getRetired(), "the retired row must come back still flagged as retired");
	}
	
	@Test
	void getAllInstances_isEmptyWhenNoTasksExist() {
		assertTrue(exporter.getAllInstances().isEmpty(), "the standard test dataset seeds no system tasks");
	}
	
	@Test
	void export_thenReimportOntoAFreshTarget(@TempDir File outDir) throws Exception {
		seedOneLiveAndOneRetiredTask();
		exporter.export(exporter.getAllInstances(), new ExportContext(outDir));
		purgeAllSystemTasks();
		assertTrue(tasksService().getAllSystemTasks(true).isEmpty(), "the target must start without the tasks");
		
		CsvFailingLines failed = replayThroughInitializer(outDir);
		
		assertTrue(failed.getFailingLines().isEmpty(), describe(failed));
		SystemTask live = tasksService().getSystemTaskByUuid(LIVE_UUID);
		assertEquals("vital-check", live.getName());
		assertEquals("Daily Vital Check", live.getTitle());
		assertEquals("Check patient vitals every day", live.getDescription());
		assertEquals("Routine monitoring required", live.getRationale());
		assertEquals(Priority.HIGH, live.getPriority());
		assertFalse(live.getRetired());
		SystemTask retired = tasksService().getSystemTaskByUuid(RETIRED_UUID);
		assertEquals("discontinued", retired.getName(), "Iniz bootstraps and fills the retired row, so name must travel");
		assertEquals("Discontinued Task", retired.getTitle());
		assertTrue(retired.getRetired(), "the retirement itself must replay on the target");
	}
	
	@Test
	void export_thenReimportOntoATargetThatAlreadyHasTheTasks(@TempDir File outDir) throws Exception {
		seedOneLiveAndOneRetiredTask();
		exporter.export(exporter.getAllInstances(), new ExportContext(outDir));
		
		CsvFailingLines failed = replayThroughInitializer(outDir);
		
		assertTrue(failed.getFailingLines().isEmpty(), describe(failed));
		assertEquals(2, tasksService().getAllSystemTasks(true).size(), "existing rows are matched by uuid, not duplicated");
		assertFalse(tasksService().getSystemTaskByUuid(LIVE_UUID).getRetired());
		assertTrue(tasksService().getSystemTaskByUuid(RETIRED_UUID).getRetired());
	}
	
	private void seedOneLiveAndOneRetiredTask() {
		SystemTask live = taskAssignedTo(nurse.getProviderRoleId());
		live.setUuid(LIVE_UUID);
		live.setDescription("Check patient vitals every day");
		live.setRationale("Routine monitoring required");
		tasksService().saveSystemTask(live);
		
		SystemTask retired = new SystemTask();
		retired.setUuid(RETIRED_UUID);
		retired.setName("discontinued");
		retired.setTitle("Discontinued Task");
		retired.setPriority(Priority.MEDIUM);
		tasksService().saveSystemTask(retired);
		tasksService().retireSystemTask(retired, "No longer needed");
		Context.flushSession();
	}
	
	private void purgeAllSystemTasks() {
		SessionFactory sessionFactory = Context.getRegisteredComponent("sessionFactory", SessionFactory.class);
		for (SystemTask task : tasksService().getAllSystemTasks(true)) {
			sessionFactory.getCurrentSession().delete(task);
		}
		sessionFactory.getCurrentSession().flush();
	}
	
	/**
	 * Feeds the exported file back through Iniz's own parser, the only thing that shows the file is
	 * loadable rather than merely well-shaped.
	 */
	private static CsvFailingLines replayThroughInitializer(File outDir) throws Exception {
		File csv = outDir.toPath().resolve(Paths.get("configuration", Domain.SYSTEM_TASKS.getName(), "systemTasks.csv"))
		        .toFile();
		assertTrue(csv.exists(), "expected " + csv);
		SystemTasksCsvParser parser = new SystemTasksCsvParser(Context.getService(TasksService.class),
		        new SystemTasksLineProcessor());
		try (InputStream in = new FileInputStream(csv)) {
			parser.setInputStream(in);
			List<String[]> lines = parser.getLines();
			assertEquals(2, lines.size(), "both seeded tasks must be in the file");
			return parser.process(lines);
		}
	}
	
	private static String describe(CsvFailingLines failed) {
		return failed.getErrorDetails().stream().map(d -> d.getCsvLine().prettyPrint() + " -> " + d.getException())
		        .collect(Collectors.joining("\n", "Iniz rejected exported lines:\n", ""));
	}
	
	private static TasksService tasksService() {
		return Context.getService(TasksService.class);
	}
	
	private static SystemTask taskAssignedTo(Integer providerRoleId) {
		SystemTask task = new SystemTask();
		task.setUuid("550e8400-e29b-41d4-a716-446655440001");
		task.setName("vital-check");
		task.setTitle("Daily Vital Check");
		task.setPriority(Priority.HIGH);
		task.setDefaultAssigneeProviderRoleId(providerRoleId);
		return task;
	}
}
