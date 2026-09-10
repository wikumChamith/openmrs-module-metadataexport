/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.metadataexport.web.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openmrs.api.context.Context;
import org.openmrs.module.initializer.Domain;
import org.openmrs.module.metadataexport.MetadataExportConstants;
import org.openmrs.module.metadataexport.api.MetadataExportService;
import org.openmrs.module.metadataexport.api.model.ExportBuild;
import org.openmrs.module.metadataexport.api.model.ExportPackage;
import org.openmrs.module.metadataexport.api.model.ExportPackageEntry;
import org.openmrs.module.metadataexport.api.model.ExportStatus;
import org.openmrs.util.OpenmrsUtil;
import org.openmrs.web.test.jupiter.BaseModuleWebContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

class ExportBuildControllerTest extends BaseModuleWebContextSensitiveTest {
	
	private static final String BUILDS = MetadataExportRestConstants.BASE + "/builds";
	
	@Autowired
	private WebApplicationContext webApplicationContext;
	
	private MockMvc mockMvc;
	
	private File appDataDir;
	
	@BeforeEach
	void setUpMockMvc(@TempDir File appDataDir) {
		this.appDataDir = appDataDir;
		OpenmrsUtil.setApplicationDataDirectory(appDataDir.getAbsolutePath());
		mockMvc = MockMvcBuilders.standaloneSetup(new ExportBuildController())
		        .setControllerAdvice(new MetadataExportControllerAdvice()).build();
	}
	
	@Test
	void download_returns409WhileTheBuildIsNotCompleted() throws Exception {
		ExportBuild build = saveBuild(ExportStatus.RUNNING);
		
		MockHttpServletResponse response = mockMvc.perform(get(BUILDS + "/" + build.getUuid() + "/download")).andReturn()
		        .getResponse();
		
		assertEquals(409, response.getStatus());
	}
	
	@Test
	void download_streamsTheZipOfACompletedBuild() throws Exception {
		byte[] zipBytes = "not really a zip".getBytes(StandardCharsets.UTF_8);
		File zip = new File(appDataDir, "metadataexport/packages/p-1/1/metadataexport-test-v1.zip");
		Files.createDirectories(zip.getParentFile().toPath());
		Files.write(zip.toPath(), zipBytes);
		ExportBuild build = saveBuild(ExportStatus.COMPLETED);
		build.setZipPath("metadataexport/packages/p-1/1/metadataexport-test-v1.zip");
		service().saveExportBuild(build);
		
		MockHttpServletResponse response = mockMvc.perform(get(BUILDS + "/" + build.getUuid() + "/download")).andReturn()
		        .getResponse();
		
		assertEquals(200, response.getStatus());
		assertEquals("application/zip", response.getContentType());
		assertNotNull(response.getHeader("Content-Disposition"));
		assertTrue(response.getHeader("Content-Disposition").contains("metadataexport-test-v1.zip"));
		assertArrayEquals(zipBytes, response.getContentAsByteArray());
	}
	
	@Test
	void download_returns410WhenTheZipFileIsGone() throws Exception {
		ExportBuild build = saveBuild(ExportStatus.COMPLETED);
		build.setZipPath("metadataexport/packages/p-1/1/deleted.zip");
		service().saveExportBuild(build);
		
		MockHttpServletResponse response = mockMvc.perform(get(BUILDS + "/" + build.getUuid() + "/download")).andReturn()
		        .getResponse();
		
		assertEquals(410, response.getStatus());
	}
	
	@Test
	void download_returns404WithAnErrorBodyForUnknownBuild() throws Exception {
		MockHttpServletResponse response = mockMvc.perform(get(BUILDS + "/no-such-uuid/download")).andReturn().getResponse();
		
		assertEquals(404, response.getStatus());
		assertTrue(response.getContentAsString().contains("no-such-uuid"), response.getContentAsString());
	}
	
	/**
	 * Read-only users can poll a build but not take the zip: getBuildZip carries the Manage privilege.
	 */
	@Test
	void download_returns403ForAUserWithOnlyTheGetPrivilege() throws Exception {
		ExportBuild build = saveBuild(ExportStatus.COMPLETED);
		Context.becomeUser("3-4"); // butch: no metadata export privileges in the standard dataset
		Context.addProxyPrivilege(MetadataExportConstants.GET_PRIVILEGE);
		try {
			assertEquals(403,
			    mockMvc.perform(get(BUILDS + "/" + build.getUuid() + "/download")).andReturn().getResponse().getStatus());
		}
		finally {
			Context.removeProxyPrivilege(MetadataExportConstants.GET_PRIVILEGE);
			authenticate();
		}
	}
	
	/**
	 * Through the real dispatcher chain rather than a standalone setup: without the
	 * ExceptionHandlerExceptionResolver in webModuleApplicationContext.xml the advice never runs and a
	 * logged-out request comes back 200 with a stack-trace body.
	 */
	@Test
	void download_returns401ThroughTheRealDispatcherWhenNotAuthenticated() throws Exception {
		MockMvc realDispatcher = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
		Context.logout();
		try {
			assertEquals(401,
			    realDispatcher.perform(get(BUILDS + "/no-such-uuid/download")).andReturn().getResponse().getStatus());
		}
		finally {
			authenticate();
		}
	}
	
	@Test
	void download_returns401WhenNotAuthenticated() throws Exception {
		ExportBuild build = saveBuild(ExportStatus.COMPLETED);
		Context.logout();
		try {
			assertEquals(401,
			    mockMvc.perform(get(BUILDS + "/" + build.getUuid() + "/download")).andReturn().getResponse().getStatus());
		}
		finally {
			authenticate();
		}
	}
	
	private ExportBuild saveBuild(ExportStatus status) {
		ExportPackage exportPackage = new ExportPackage();
		exportPackage.setName("Build test " + System.nanoTime());
		exportPackage.setDescription("test");
		ExportPackageEntry entry = new ExportPackageEntry();
		entry.setDomain(Domain.LOCATIONS.name());
		exportPackage.getEntries().add(entry);
		exportPackage = service().saveExportPackage(exportPackage);
		
		ExportBuild build = new ExportBuild();
		build.setExportPackage(exportPackage);
		build.setVersion(1);
		build.setExportStatus(status);
		return service().saveExportBuild(build);
	}
	
	private static MetadataExportService service() {
		return Context.getService(MetadataExportService.class);
	}
}
