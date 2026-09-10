/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.metadataexport.web.resource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openmrs.module.MetadataExportDaemonTokenTestSupport;
import org.openmrs.module.metadataexport.api.model.ExportBuild;
import org.openmrs.module.metadataexport.api.model.ExportPackage;
import org.openmrs.module.metadataexport.api.model.ExportStatus;
import org.openmrs.module.metadataexport.web.controller.MetadataExportRestController;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.openmrs.module.webservices.rest.web.response.ConversionException;
import org.openmrs.module.webservices.rest.web.response.ObjectNotFoundException;
import org.openmrs.module.webservices.rest.web.response.ResourceDoesNotSupportOperationException;
import org.openmrs.module.webservices.rest.web.v1_0.controller.jupiter.MainResourceControllerTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openmrs.module.metadataexport.web.resource.ExportPackageResourceTest.causeChain;
import static org.openmrs.module.metadataexport.web.resource.ExportPackageResourceTest.save;
import static org.openmrs.module.metadataexport.web.resource.ExportPackageResourceTest.saveBuild;
import static org.openmrs.module.metadataexport.web.resource.ExportPackageResourceTest.service;

class ExportBuildResourceTest extends MainResourceControllerTest {
	
	private ExportPackage exportPackage;
	
	private ExportBuild completed;
	
	@BeforeEach
	void saveACompletedBuild() {
		exportPackage = save("Site A locations", "loc-1");
		completed = saveBuild(exportPackage, 1, ExportStatus.COMPLETED);
	}
	
	@Override
	public String getURI() {
		return "metadataexport/builds";
	}
	
	@Override
	public String getUuid() {
		return completed.getUuid();
	}
	
	@Override
	public long getAllCount() {
		return 0; // unused: there is no unfiltered list, see shouldGetAll
	}
	
	/** Replaces the base test: listing needs {@code ?package=}, an unfiltered GET is refused. */
	@Override
	@Test
	public void shouldGetAll() throws Exception {
		assertThrows(ResourceDoesNotSupportOperationException.class, () -> handle(newGetRequest(getURI())));
		
		MockHttpServletRequest request = request(RequestMethod.GET, getURI());
		request.addParameter("package", exportPackage.getUuid());
		SimpleObject body = deserialize(handle(request));
		
		assertEquals(1, ((List<?>) body.get("results")).size());
	}
	
	@Test
	void defaultRep_carriesStatusPackageRefAndDownloadUrlOnceCompleted() throws Exception {
		SimpleObject body = deserialize(handle(newGetRequest(getURI() + "/" + getUuid())));
		
		assertEquals("COMPLETED", body.get("status"));
		assertEquals(1, body.<Object> get("version"));
		assertEquals("Site A locations v1 (COMPLETED)", body.get("display"));
		Map<String, Object> pkg = body.get("package");
		assertEquals(exportPackage.getUuid(), pkg.get("uuid"));
		String downloadUrl = body.get("downloadUrl");
		assertTrue(downloadUrl.startsWith("http"), "absolute: " + downloadUrl);
		assertTrue(downloadUrl.endsWith("/ws/rest/v1/metadataexport/builds/" + getUuid() + "/download"), downloadUrl);
		assertTrue(body.containsKey("errorMessage"));
	}
	
	@Test
	void defaultRep_hasNoDownloadUrlWhileNotCompleted() throws Exception {
		ExportBuild queued = saveBuild(exportPackage, 2, ExportStatus.QUEUED);
		
		SimpleObject body = deserialize(handle(newGetRequest(getURI() + "/" + queued.getUuid())));
		
		assertEquals("QUEUED", body.get("status"));
		assertNull(body.get("downloadUrl"));
	}
	
	@Test
	void get_unknownUuidIsNotFound() {
		assertThrows(ObjectNotFoundException.class, () -> handle(newGetRequest(getURI() + "/no-such-uuid")));
	}
	
	@Test
	void fullRep_includesTheParsedManifest() throws Exception {
		completed.setManifestJson("{\"version\":1,\"domains\":[\"LOCATIONS\"]}");
		service().saveExportBuild(completed);
		
		MockHttpServletRequest request = request(RequestMethod.GET, getURI() + "/" + getUuid());
		request.addParameter("v", "full");
		SimpleObject body = deserialize(handle(request));
		
		Map<String, Object> manifest = body.get("manifest");
		assertEquals(1, manifest.get("version"));
		assertEquals(Arrays.asList("LOCATIONS"), manifest.get("domains"));
	}
	
	/**
	 * The manifest is this module's own output, so a corrupt one must surface, not read as "no
	 * manifest". The framework wraps a getter's exception in a ConversionException whose cause chain
	 * carries the detail into the error body.
	 */
	@Test
	void fullRep_failsLoudlyOnACorruptManifest() {
		completed.setManifestJson("{not json");
		service().saveExportBuild(completed);
		MockHttpServletRequest request = request(RequestMethod.GET, getURI() + "/" + getUuid());
		request.addParameter("v", "full");
		
		ConversionException e = assertThrows(ConversionException.class, () -> handle(request));
		
		assertTrue(causeChain(e).contains("manifest of build " + getUuid() + " could not be parsed"), causeChain(e));
	}
	
	@Test
	void search_byPackageIsPagedNewestFirstWithTotalCount() throws Exception {
		saveBuild(exportPackage, 2, ExportStatus.FAILED);
		saveBuild(exportPackage, 3, ExportStatus.COMPLETED);
		
		MockHttpServletRequest request = request(RequestMethod.GET, getURI());
		request.addParameter("package", exportPackage.getUuid());
		request.addParameter("limit", "2");
		request.addParameter("totalCount", "true");
		request.addParameter("v", "default"); // lists are refs by default; versions live in the default rep
		SimpleObject body = deserialize(handle(request));
		
		List<Map<String, Object>> results = body.get("results");
		assertEquals(Arrays.asList(3, 2), Arrays.asList(results.get(0).get("version"), results.get(1).get("version")));
		assertEquals(3, body.<Object> get("totalCount"));
		List<Map<String, Object>> links = body.get("links");
		assertEquals(1, links.size());
		assertEquals("next", links.get(0).get("rel"));
		assertTrue(links.get(0).get("uri").toString().contains("startIndex=2"), links.get(0).get("uri").toString());
		
		request = request(RequestMethod.GET, getURI());
		request.addParameter("package", exportPackage.getUuid());
		request.addParameter("limit", "2");
		request.addParameter("startIndex", "2");
		SimpleObject last = deserialize(handle(request));
		
		assertEquals(1, ((List<?>) last.get("results")).size());
		List<Map<String, Object>> lastLinks = last.get("links");
		assertEquals(1, lastLinks.size(), "last page: prev only");
		assertEquals("prev", lastLinks.get(0).get("rel"));
	}
	
	/**
	 * The package is the only selector, so an unknown one is a 404, not an empty list to poll forever.
	 */
	@Test
	void search_forAnUnknownPackageIsNotFound() {
		MockHttpServletRequest request = request(RequestMethod.GET, getURI());
		request.addParameter("package", "no-such-uuid");
		
		ObjectNotFoundException e = assertThrows(ObjectNotFoundException.class, () -> handle(request));
		
		assertTrue(e.getMessage().contains("no-such-uuid"), e.getMessage());
	}
	
	/**
	 * The daemon thread this starts runs in its own session and cannot see this test's uncommitted
	 * rows, so it fails harmlessly (logged) and cannot affect the assertions.
	 */
	@Test
	void create_triggersABuildAndReturns201WithTheQueuedBuild() throws Exception {
		MetadataExportDaemonTokenTestSupport.ensureDaemonToken();
		
		MockHttpServletResponse response = handle(
		    newPostRequest(getURI(), "{\"package\":\"" + exportPackage.getUuid() + "\"}"));
		
		assertEquals(201, response.getStatus());
		SimpleObject body = deserialize(response);
		assertEquals("QUEUED", body.get("status"));
		assertEquals(2, body.<Object> get("version"));
		assertNull(body.get("downloadUrl"));
		assertNotNull(service().getBuildByUuid(body.get("uuid").toString()));
		Map<String, Object> pkg = body.get("package");
		assertEquals(exportPackage.getUuid(), pkg.get("uuid"));
	}
	
	@Test
	void create_conflictsWhileABuildIsActive() {
		saveBuild(exportPackage, 2, ExportStatus.RUNNING);
		
		ConflictException e = assertThrows(ConflictException.class,
		    () -> handle(newPostRequest(getURI(), "{\"package\":\"" + exportPackage.getUuid() + "\"}")));
		
		assertTrue(e.getMessage().contains("RUNNING"), e.getMessage());
	}
	
	@Test
	void create_conflictsForARetiredPackage() {
		service().retireExportPackage(exportPackage, "obsolete");
		
		ConflictException e = assertThrows(ConflictException.class,
		    () -> handle(newPostRequest(getURI(), "{\"package\":\"" + exportPackage.getUuid() + "\"}")));
		
		assertTrue(e.getMessage().contains("obsolete"), e.getMessage());
	}
	
	@Test
	void conflictException_isA409InProduction() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();
		
		new MetadataExportRestController().handleException(new ConflictException("busy"), new MockHttpServletRequest(),
		    response);
		
		assertEquals(409, response.getStatus());
	}
	
	@Test
	void create_rejectsAMissingPackage() {
		assertThrows(ConversionException.class, () -> handle(newPostRequest(getURI(), "{}")));
	}
	
	@Test
	void create_rejectsAnUnknownPackageNamingTheUuid() {
		ConversionException e = assertThrows(ConversionException.class,
		    () -> handle(newPostRequest(getURI(), "{\"package\":\"no-such-uuid\"}")));
		
		assertTrue(causeChain(e).contains("No export package with uuid 'no-such-uuid'"), causeChain(e));
	}
	
	/**
	 * Without the override the framework would treat this as an update and re-run save(), i.e. trigger.
	 */
	@Test
	void update_isRefusedAndTriggersNothing() {
		assertThrows(ResourceDoesNotSupportOperationException.class,
		    () -> handle(newPostRequest(getURI() + "/" + getUuid(), "{}")));
		assertThrows(ResourceDoesNotSupportOperationException.class,
		    () -> handle(newPostRequest(getURI() + "/" + getUuid(), "{\"package\":\"" + save("Other").getUuid() + "\"}")));
		
		assertEquals(1, service().getCountOfBuilds(exportPackage));
		assertEquals(exportPackage.getUuid(), service().getBuildByUuid(getUuid()).getExportPackage().getUuid());
	}
	
	@Test
	void delete_isRefusedBecauseBuildsAreHistory() {
		assertThrows(ResourceDoesNotSupportOperationException.class,
		    () -> handle(newDeleteRequest(getURI() + "/" + getUuid())));
		assertNotNull(service().getBuildByUuid(getUuid()));
	}
}
