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
import org.openmrs.api.APIAuthenticationException;
import org.openmrs.api.context.Context;
import org.openmrs.module.initializer.Domain;
import org.openmrs.module.metadataexport.api.MetadataExportService;
import org.openmrs.module.metadataexport.api.model.ExportBuild;
import org.openmrs.module.metadataexport.api.model.ExportPackage;
import org.openmrs.module.metadataexport.api.model.ExportPackageEntry;
import org.openmrs.module.metadataexport.api.model.ExportStatus;
import org.openmrs.module.metadataexport.web.controller.MetadataExportRestController;
import org.openmrs.module.metadataexport.web.controller.MetadataExportRestConstants;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.openmrs.module.webservices.rest.web.response.ConversionException;
import org.openmrs.module.webservices.rest.web.response.IllegalRequestException;
import org.openmrs.module.webservices.rest.web.response.ObjectNotFoundException;
import org.openmrs.module.webservices.rest.web.response.ResourceDoesNotSupportOperationException;
import org.openmrs.module.webservices.rest.web.v1_0.controller.jupiter.MainResourceControllerTest;
import org.openmrs.module.webservices.validation.ValidationException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives the REST module's real handler chain, so these cover routing of the namespaced resource,
 * the standard envelope/paging and this module's own resource logic together. One harness quirk
 * shared with the REST module's own tests: {@code handle()} invokes the handler adapter directly,
 * so error paths surface as the thrown exception rather than the status the base controller maps it
 * to in production. Lists are {@code ref} representations by the REST module's own convention.
 */
class ExportPackageResourceTest extends MainResourceControllerTest {
	
	private ExportPackage saved;
	
	@BeforeEach
	void savePackage() {
		saved = save("Site A locations", "loc-1");
	}
	
	@Override
	public String getURI() {
		return "metadataexport/packages";
	}
	
	@Override
	public String getUuid() {
		return saved.getUuid();
	}
	
	@Override
	public long getAllCount() {
		return service().getCountOfPackages(false);
	}
	
	@Test
	void defaultRep_exposesEntriesAndLatestBuild() throws Exception {
		saveBuild(saved, 1, ExportStatus.FAILED);
		
		SimpleObject body = deserialize(handle(newGetRequest(getURI() + "/" + getUuid())));
		
		assertEquals("Site A locations", body.get("name"));
		assertEquals("Site A locations", body.get("display"));
		List<Map<String, Object>> entries = body.get("entries");
		assertEquals(1, entries.size());
		assertEquals("LOCATIONS", entries.get(0).get("domain"));
		assertEquals(Collections.singletonList("loc-1"), entries.get(0).get("itemUuids"));
		Map<String, Object> latestBuild = body.get("latestBuild");
		assertEquals("FAILED", latestBuild.get("status"));
		assertEquals(1, latestBuild.get("version"));
	}
	
	@Test
	void defaultRep_hasNullLatestBuildBeforeAnyBuild() throws Exception {
		SimpleObject body = deserialize(handle(newGetRequest(getURI() + "/" + getUuid())));
		
		assertTrue(body.containsKey("latestBuild"));
		assertNull(body.get("latestBuild"));
	}
	
	@Test
	void get_unknownUuidIsNotFound() {
		assertThrows(ObjectNotFoundException.class, () -> handle(newGetRequest(getURI() + "/no-such-uuid")));
	}
	
	@Test
	void create_persistsNormalisesDomainAndDedupesUuidsWith201() throws Exception {
		String json = "{\"name\":\"New\",\"description\":\"d\",\"entries\":[{\"domain\":\"locations\",\"itemUuids\":[\"x\",\"x\"]}]}";
		
		MockHttpServletResponse response = handle(newPostRequest(getURI(), json));
		
		assertEquals(201, response.getStatus());
		SimpleObject body = deserialize(response);
		List<Map<String, Object>> entries = body.get("entries");
		assertEquals("LOCATIONS", entries.get(0).get("domain"));
		assertEquals(Collections.singletonList("x"), entries.get(0).get("itemUuids"));
		ExportPackage persisted = service().getPackageByUuid(body.get("uuid").toString());
		assertNotNull(persisted);
		assertEquals("New", persisted.getName());
		assertEquals(1, persisted.getEntries().size());
		assertEquals("LOCATIONS", persisted.getEntries().get(0).getDomain());
		assertEquals(Collections.singletonList("x"), persisted.getEntries().get(0).getItemUuids());
	}
	
	@Test
	void create_acceptsAnExplicitlyEmptyEntriesListMeaningEveryDomain() throws Exception {
		MockHttpServletResponse response = handle(newPostRequest(getURI(), "{\"name\":\"Everything\",\"entries\":[]}"));
		
		assertEquals(201, response.getStatus());
		SimpleObject body = deserialize(response);
		assertEquals(0, ((List<?>) body.get("entries")).size());
	}
	
	@Test
	void create_anEntryWithoutItemUuidsMeansTheWholeDomain() throws Exception {
		MockHttpServletResponse response = handle(
		    newPostRequest(getURI(), "{\"name\":\"Whole domain\",\"entries\":[{\"domain\":\"locations\"}]}"));
		
		assertEquals(201, response.getStatus());
		ExportPackage persisted = service().getPackageByUuid(deserialize(response).get("uuid").toString());
		assertTrue(persisted.getEntries().get(0).getItemUuids().isEmpty());
	}
	
	@Test
	void create_rejectsAMissingEntriesList() {
		ConversionException e = assertThrows(ConversionException.class,
		    () -> handle(newPostRequest(getURI(), "{\"name\":\"Partial\"}")));
		
		assertTrue(e.getMessage().contains("entries"), e.getMessage());
	}
	
	/**
	 * Read as "no uuids" this would silently export the whole domain. The framework wraps a setter's
	 * exception in its own ConversionException and, in production, joins the cause messages into the
	 * error body, so the detail is asserted on the cause chain.
	 */
	@Test
	void create_rejectsANonListItemUuidsInsteadOfWideningTheExport() {
		ConversionException e = assertThrows(ConversionException.class, () -> handle(newPostRequest(getURI(),
		    "{\"name\":\"Bad\",\"entries\":[{\"domain\":\"locations\",\"itemUuids\":\"loc-1\"}]}")));
		
		assertTrue(causeChain(e).contains("entries[0].itemUuids must be a list"), causeChain(e));
	}
	
	@Test
	void create_rejectsAMisspelledEntryKey() {
		ConversionException e = assertThrows(ConversionException.class, () -> handle(newPostRequest(getURI(),
		    "{\"name\":\"Bad\",\"entries\":[{\"domain\":\"locations\",\"itemUuid\":[\"loc-1\"]}]}")));
		
		assertTrue(causeChain(e).contains("unknown properties [itemUuid]"), causeChain(e));
	}
	
	@Test
	void create_rejectsAnEntryWithoutADomain() {
		ConversionException e = assertThrows(ConversionException.class,
		    () -> handle(newPostRequest(getURI(), "{\"name\":\"Bad\",\"entries\":[{\"itemUuids\":[\"loc-1\"]}]}")));
		
		assertTrue(causeChain(e).contains("entries[0].domain is required"), causeChain(e));
	}
	
	@Test
	void create_rejectsAnEntryThatIsNotAnObject() {
		assertThrows(ConversionException.class,
		    () -> handle(newPostRequest(getURI(), "{\"name\":\"Bad\",\"entries\":[\"LOCATIONS\"]}")));
	}
	
	@Test
	void create_rejectsADuplicateNameAsAValidationErrorOnTheNameField() {
		ValidationException e = assertThrows(ValidationException.class, () -> handle(
		    newPostRequest(getURI(), "{\"name\":\"Site A locations\",\"entries\":[{\"domain\":\"locations\"}]}")));
		
		assertNotNull(e.getErrors().getFieldError("name"), String.valueOf(e.getErrors()));
	}
	
	@Test
	void create_rejectsAnUnknownDomainAsAValidationErrorOnTheEntryField() {
		ValidationException e = assertThrows(ValidationException.class,
		    () -> handle(newPostRequest(getURI(), "{\"name\":\"Bad\",\"entries\":[{\"domain\":\"not_a_domain\"}]}")));
		
		assertNotNull(e.getErrors().getFieldError("entries[0].domain"), String.valueOf(e.getErrors()));
	}
	
	@Test
	void update_replacesNameAndEntries() throws Exception {
		String json = "{\"name\":\"After\",\"entries\":[{\"domain\":\"encounter_types\",\"itemUuids\":[\"et-1\"]}]}";
		
		MockHttpServletResponse response = handle(newPostRequest(getURI() + "/" + getUuid(), json));
		
		assertEquals(200, response.getStatus());
		ExportPackage reloaded = service().getPackageByUuid(getUuid());
		assertEquals("After", reloaded.getName());
		assertEquals(1, reloaded.getEntries().size());
		assertEquals("ENCOUNTER_TYPES", reloaded.getEntries().get(0).getDomain());
	}
	
	/**
	 * REST module updates are partial: an omitted property is left alone, so nothing widens silently.
	 */
	@Test
	void update_withoutEntriesKeepsTheExistingEntries() throws Exception {
		assertEquals(200, handle(newPostRequest(getURI() + "/" + getUuid(), "{\"name\":\"Renamed\"}")).getStatus());
		ExportPackage reloaded = service().getPackageByUuid(getUuid());
		assertEquals("Renamed", reloaded.getName());
		assertEquals(1, reloaded.getEntries().size());
		assertEquals(Collections.singletonList("loc-1"), reloaded.getEntries().get(0).getItemUuids());
	}
	
	@Test
	void update_withEmptyEntriesWidensToEveryDomain() throws Exception {
		assertEquals(200, handle(newPostRequest(getURI() + "/" + getUuid(), "{\"entries\":[]}")).getStatus());
		assertTrue(service().getPackageByUuid(getUuid()).getEntries().isEmpty());
	}
	
	@Test
	void delete_retiresWithTheGivenReason() throws Exception {
		MockHttpServletRequest request = request(RequestMethod.DELETE, getURI() + "/" + getUuid());
		request.addParameter("reason", "obsolete");
		
		assertEquals(204, handle(request).getStatus());
		ExportPackage reloaded = service().getPackageByUuid(getUuid());
		assertTrue(reloaded.getRetired());
		assertEquals("obsolete", reloaded.getRetireReason());
	}
	
	@Test
	void purge_isNotSupported() {
		MockHttpServletRequest request = request(RequestMethod.DELETE, getURI() + "/" + getUuid());
		request.addParameter("purge", "true");
		
		assertThrows(ResourceDoesNotSupportOperationException.class, () -> handle(request));
		assertNotNull(service().getPackageByUuid(getUuid()));
	}
	
	@Test
	void getAll_isSortedByNameAndPagedWithLinksAndTotalCount() throws Exception {
		save("Alpha");
		save("Zulu");
		
		MockHttpServletRequest request = request(RequestMethod.GET, getURI());
		request.addParameter("limit", "2");
		request.addParameter("totalCount", "true");
		SimpleObject first = deserialize(handle(request));
		
		assertEquals(Arrays.asList("Alpha", "Site A locations"), displays(first.get("results")));
		assertEquals(3, first.<Object> get("totalCount"));
		List<Map<String, Object>> links = first.get("links");
		assertEquals(1, links.size());
		assertEquals("next", links.get(0).get("rel"));
		assertTrue(links.get(0).get("uri").toString().contains("startIndex=2"), links.get(0).get("uri").toString());
		
		request = request(RequestMethod.GET, getURI());
		request.addParameter("limit", "2");
		request.addParameter("startIndex", "2");
		SimpleObject second = deserialize(handle(request));
		
		assertEquals(Collections.singletonList("Zulu"), displays(second.get("results")));
		List<Map<String, Object>> secondLinks = second.get("links");
		assertEquals(1, secondLinks.size(), "last page: prev only");
		assertEquals("prev", secondLinks.get(0).get("rel"));
	}
	
	@Test
	void getAll_rejectsANegativeStartIndex() {
		MockHttpServletRequest request = request(RequestMethod.GET, getURI());
		request.addParameter("startIndex", "-1");
		
		assertThrows(IllegalRequestException.class, () -> handle(request));
	}
	
	@Test
	void getAll_excludesRetiredUnlessIncludeAll() throws Exception {
		service().retireExportPackage(save("Old"), "obsolete");
		
		SimpleObject live = deserialize(handle(newGetRequest(getURI())));
		MockHttpServletRequest request = request(RequestMethod.GET, getURI());
		request.addParameter("includeAll", "true");
		SimpleObject all = deserialize(handle(request));
		
		assertEquals(Collections.singletonList("Site A locations"), displays(live.get("results")));
		assertEquals(Arrays.asList("Old", "Site A locations"), displays(all.get("results")));
	}
	
	/** The base controller maps this to 401 (or 403 when logged in without the privilege). */
	@Test
	void requestsAreRefusedWhenNotAuthenticated() {
		Context.logout();
		try {
			assertThrows(APIAuthenticationException.class, () -> handle(newGetRequest(getURI())));
		}
		finally {
			authenticate();
		}
	}
	
	@Test
	void namespaceController_claimsTheResourcesNamespace() {
		assertTrue(getURI().startsWith(MetadataExportRestConstants.NAMESPACE.substring("v1/".length()) + "/"));
		assertEquals(MetadataExportRestConstants.NAMESPACE, new MetadataExportRestController().getNamespace());
	}
	
	/** What the REST base controller puts in the error body: every message down the cause chain. */
	static String causeChain(Throwable t) {
		StringBuilder messages = new StringBuilder();
		for (Throwable cause = t; cause != null; cause = cause.getCause()) {
			messages.append(cause.getMessage()).append(" => ");
		}
		return messages.toString();
	}
	
	/** List results are refs; for a metadata resource {@code display} is the name. */
	private static List<String> displays(List<Map<String, Object>> results) {
		List<String> displays = new ArrayList<>();
		for (Map<String, Object> result : results) {
			displays.add(result.get("display").toString());
		}
		return displays;
	}
	
	static ExportPackage save(String name, String... itemUuids) {
		ExportPackage exportPackage = new ExportPackage();
		exportPackage.setName(name);
		exportPackage.setDescription("test");
		ExportPackageEntry entry = new ExportPackageEntry();
		entry.setDomain(Domain.LOCATIONS.name());
		entry.getItemUuids().addAll(Arrays.asList(itemUuids));
		exportPackage.getEntries().add(entry);
		return service().saveExportPackage(exportPackage);
	}
	
	static ExportBuild saveBuild(ExportPackage exportPackage, int version, ExportStatus status) {
		ExportBuild build = new ExportBuild();
		build.setExportPackage(exportPackage);
		build.setVersion(version);
		build.setExportStatus(status);
		return service().saveExportBuild(build);
	}
	
	static MetadataExportService service() {
		return Context.getService(MetadataExportService.class);
	}
}
