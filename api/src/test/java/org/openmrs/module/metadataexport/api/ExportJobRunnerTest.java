/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.metadataexport.api;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openmrs.api.APIException;
import org.openmrs.api.context.Context;
import org.openmrs.module.initializer.Domain;
import org.openmrs.module.metadataexport.api.model.ExportBuild;
import org.openmrs.module.metadataexport.api.model.ExportPackage;
import org.openmrs.module.metadataexport.api.model.ExportPackageEntry;
import org.openmrs.module.metadataexport.api.model.ExportStatus;
import org.openmrs.test.jupiter.BaseModuleContextSensitiveTest;
import org.openmrs.util.OpenmrsUtil;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExportJobRunnerTest extends BaseModuleContextSensitiveTest {
	
	@Autowired
	private ExportJobRunner runner;
	
	private MetadataExportService service;
	
	@BeforeEach
	void setUp(@TempDir File appDataDir) {
		service = Context.getService(MetadataExportService.class);
		OpenmrsUtil.setApplicationDataDirectory(appDataDir.getAbsolutePath());
	}
	
	@Test
	void execute_completesABuild() {
		ExportBuild build = queuedBuild(savePackage("Happy", Domain.LOCATIONS.name()));
		
		runner.execute(build.getUuid());
		
		ExportBuild reloaded = service.getBuildByUuid(build.getUuid());
		assertEquals(ExportStatus.COMPLETED, reloaded.getExportStatus());
		assertNotNull(reloaded.getDateStarted());
		assertNotNull(reloaded.getDateCompleted());
		assertTrue(service.getBuildZip(reloaded).exists());
	}
	
	@Test
	void execute_marksAFailedBuildWithTheRootCause() {
		ExportBuild build = queuedBuild(savePackage("Broken", Domain.LOCATIONS.name(), "no-such-uuid"));
		
		runner.execute(build.getUuid());
		
		ExportBuild reloaded = service.getBuildByUuid(build.getUuid());
		assertEquals(ExportStatus.FAILED, reloaded.getExportStatus());
		assertNotNull(reloaded.getDateCompleted());
		assertTrue(reloaded.getErrorMessage().contains("no-such-uuid"), reloaded.getErrorMessage());
	}
	
	@Test
	void trigger_rejectsARetiredPackage() {
		ExportPackage exportPackage = savePackage("Shelved", Domain.LOCATIONS.name());
		service.retireExportPackage(exportPackage, "obsolete");
		
		RetiredPackageException e = assertThrows(RetiredPackageException.class,
		    () -> runner.trigger(exportPackage.getUuid()));
		
		assertTrue(e.getMessage().contains("obsolete"), e.getMessage());
	}
	
	@Test
	void trigger_rejectsASecondBuildWhileOneIsActive() {
		ExportPackage exportPackage = savePackage("Busy", Domain.LOCATIONS.name());
		ExportBuild running = queuedBuild(exportPackage);
		running.setExportStatus(ExportStatus.RUNNING);
		service.saveExportBuild(running);
		
		assertThrows(ActiveBuildException.class, () -> runner.trigger(exportPackage.getUuid()));
	}
	
	@Test
	void trigger_assignsTheNextVersionAndFailsTheBuildWhenTheDaemonCannotStart() {
		ExportPackage exportPackage = savePackage("Versioned", Domain.LOCATIONS.name());
		ExportBuild first = queuedBuild(exportPackage);
		first.setExportStatus(ExportStatus.COMPLETED);
		service.saveExportBuild(first);
		
		// no daemon token in tests, so the launch fails after the QUEUED build is saved
		assertThrows(APIException.class, () -> runner.trigger(exportPackage.getUuid()));
		
		List<ExportBuild> builds = service.getBuilds(exportPackage);
		assertEquals(2, builds.size());
		ExportBuild second = builds.get(0);
		assertEquals(2, second.getVersion());
		assertEquals(ExportStatus.FAILED, second.getExportStatus());
		assertTrue(second.getErrorMessage().contains("daemon"), second.getErrorMessage());
	}
	
	@Test
	void getLatestBuild_returnsTheNewestVersionOnly() {
		ExportPackage exportPackage = savePackage("History", Domain.LOCATIONS.name());
		ExportBuild first = queuedBuild(exportPackage);
		first.setExportStatus(ExportStatus.COMPLETED);
		service.saveExportBuild(first);
		ExportBuild second = new ExportBuild();
		second.setExportPackage(exportPackage);
		second.setVersion(2);
		second.setExportStatus(ExportStatus.FAILED);
		service.saveExportBuild(second);
		
		ExportBuild latest = service.getLatestBuild(exportPackage);
		
		assertEquals(2, latest.getVersion());
		assertEquals(ExportStatus.FAILED, latest.getExportStatus());
	}
	
	@Test
	void saveExportBuild_enforcesOneRowPerPackageAndVersion() {
		ExportPackage exportPackage = savePackage("Constrained", Domain.LOCATIONS.name());
		queuedBuild(exportPackage);
		
		ExportBuild duplicate = new ExportBuild();
		duplicate.setExportPackage(exportPackage);
		duplicate.setVersion(1);
		duplicate.setExportStatus(ExportStatus.QUEUED);
		
		RuntimeException e = assertThrows(RuntimeException.class, () -> service.saveExportBuild(duplicate));
		assertTrue(ExceptionUtils.indexOfType(e, ConstraintViolationException.class) != -1,
		    "the duplicate must surface as the violation trigger() translates to a 409: " + e);
	}
	
	private ExportPackage savePackage(String name, String domain, String... itemUuids) {
		ExportPackage exportPackage = new ExportPackage();
		exportPackage.setName(name);
		exportPackage.setDescription("test");
		ExportPackageEntry entry = new ExportPackageEntry();
		entry.setDomain(domain);
		entry.getItemUuids().addAll(Arrays.asList(itemUuids));
		exportPackage.getEntries().add(entry);
		return service.saveExportPackage(exportPackage);
	}
	
	private ExportBuild queuedBuild(ExportPackage exportPackage) {
		ExportBuild build = new ExportBuild();
		build.setExportPackage(exportPackage);
		build.setVersion(1);
		build.setExportStatus(ExportStatus.QUEUED);
		return service.saveExportBuild(build);
	}
}
