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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openmrs.Location;
import org.openmrs.api.APIAuthenticationException;
import org.openmrs.api.ValidationException;
import org.openmrs.api.context.Context;
import org.openmrs.module.initializer.Domain;
import org.openmrs.module.metadataexport.MetadataExportConstants;
import org.openmrs.module.metadataexport.api.model.ExportBuild;
import org.openmrs.module.metadataexport.api.model.ExportPackage;
import org.openmrs.module.metadataexport.api.model.ExportPackageEntry;
import org.openmrs.module.metadataexport.api.model.ExportStatus;
import org.openmrs.test.jupiter.BaseModuleContextSensitiveTest;
import org.openmrs.util.OpenmrsUtil;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetadataExportServiceTest extends BaseModuleContextSensitiveTest {
	
	private MetadataExportService service;
	
	@BeforeEach
	void setUp(@TempDir File appDataDir) {
		service = Context.getService(MetadataExportService.class);
		OpenmrsUtil.setApplicationDataDirectory(appDataDir.getAbsolutePath());
	}
	
	@Test
	void savePackage_roundTripsTheDefinitionWithEntries() {
		ExportPackage saved = service
		        .saveExportPackage(packageWith("Site A locations", Domain.LOCATIONS.name(), "u-1", "u-2"));
		
		ExportPackage loaded = service.getPackageByUuid(saved.getUuid());
		
		assertEquals("Site A locations", loaded.getName());
		assertEquals(1, loaded.getEntries().size());
		ExportPackageEntry entry = loaded.getEntries().get(0);
		assertEquals(Domain.LOCATIONS.name(), entry.getDomain());
		assertEquals(Arrays.asList("u-1", "u-2"), entry.getItemUuids());
	}
	
	@Test
	void savePackage_rejectsADuplicateName() {
		service.saveExportPackage(packageWith("Dup", Domain.LOCATIONS.name()));
		
		assertThrows(ValidationException.class,
		    () -> service.saveExportPackage(packageWith("Dup", Domain.LOCATIONS.name())));
	}
	
	@Test
	void savePackage_rejectsAnUnknownDomain() {
		assertThrows(ValidationException.class, () -> service.saveExportPackage(packageWith("Bad", "NOT_A_DOMAIN")));
	}
	
	@Test
	void savePackage_rejectsADomainWithoutARegisteredExporter() {
		assertThrows(ValidationException.class, () -> service.saveExportPackage(packageWith("Forms pkg", "HTML_FORMS")));
	}
	
	@Test
	void savePackage_rejectsAnOverlongItemUuid() {
		String overlong = String.join("", java.util.Collections.nCopies(61, "x"));
		
		assertThrows(ValidationException.class,
		    () -> service.saveExportPackage(packageWith("Overlong", Domain.LOCATIONS.name(), overlong)));
	}
	
	@Test
	void savePackage_rejectsANullItemUuid() {
		assertThrows(ValidationException.class,
		    () -> service.saveExportPackage(packageWith("Null uuid", Domain.LOCATIONS.name(), (String) null)));
	}
	
	@Test
	void savePackage_allowsReusingTheNameOfARetiredPackage() {
		ExportPackage old = service.saveExportPackage(packageWith("Reused name", Domain.LOCATIONS.name()));
		service.retireExportPackage(old, "obsolete");
		// two separate requests in reality; in one test transaction the retire must be flushed
		// before the name lookup can see it
		Context.flushSession();
		
		ExportPackage recreated = service.saveExportPackage(packageWith("Reused name", Domain.LOCATIONS.name()));
		
		assertNotEquals(old.getUuid(), recreated.getUuid());
	}
	
	@Test
	void savePackage_allowsResavingUnderItsOwnName() {
		ExportPackage saved = service.saveExportPackage(packageWith("Same", Domain.LOCATIONS.name()));
		saved.setDescription("updated");
		
		service.saveExportPackage(saved);
		
		assertEquals("updated", service.getPackageByUuid(saved.getUuid()).getDescription());
	}
	
	@Test
	void getPackages_pagesInNameOrderWithTheFullCount() {
		service.saveExportPackage(packageWith("Charlie", Domain.LOCATIONS.name()));
		service.saveExportPackage(packageWith("Alpha", Domain.LOCATIONS.name()));
		service.saveExportPackage(packageWith("Bravo", Domain.LOCATIONS.name()));
		
		assertEquals(3, service.getCountOfPackages(false));
		assertEquals(Arrays.asList("Alpha", "Bravo"), names(service.getPackages(false, 0, 2)));
		assertEquals(Collections.singletonList("Charlie"), names(service.getPackages(false, 2, 2)));
		assertTrue(service.getPackages(false, 4, 2).isEmpty(), "past the end is empty, not an error");
	}
	
	@Test
	void getPackages_countAndRowsHonourIncludeRetiredTogether() {
		ExportPackage old = service.saveExportPackage(packageWith("Old", Domain.LOCATIONS.name()));
		service.retireExportPackage(old, "obsolete");
		service.saveExportPackage(packageWith("Current", Domain.LOCATIONS.name()));
		
		assertEquals(1, service.getCountOfPackages(false));
		assertEquals(Collections.singletonList("Current"), names(service.getPackages(false, 0, 10)));
		assertEquals(2, service.getCountOfPackages(true));
		assertEquals(Arrays.asList("Current", "Old"), names(service.getPackages(true, 0, 10)));
	}
	
	/**
	 * The name is only unique among unretired packages; the id tie-break keeps pages from overlapping.
	 */
	@Test
	void getPackages_pagesDoNotOverlapForARetiredAndALivePackageOfTheSameName() {
		ExportPackage old = service.saveExportPackage(packageWith("Dup", Domain.LOCATIONS.name()));
		service.retireExportPackage(old, "obsolete");
		Context.flushSession();
		ExportPackage current = service.saveExportPackage(packageWith("Dup", Domain.LOCATIONS.name()));
		
		List<ExportPackage> first = service.getPackages(true, 0, 1);
		List<ExportPackage> second = service.getPackages(true, 1, 1);
		
		assertEquals(old.getUuid(), first.get(0).getUuid());
		assertEquals(current.getUuid(), second.get(0).getUuid());
	}
	
	@Test
	void getPackages_rejectsAnInvalidWindow() {
		assertThrows(IllegalArgumentException.class, () -> service.getPackages(false, -1, 10));
		assertThrows(IllegalArgumentException.class, () -> service.getPackages(false, 0, 0));
	}
	
	/** Every other test runs as the superuser, who bypasses @Authorized entirely. */
	@Test
	void privileges_areEnforcedForANonSuperuser() {
		ExportPackage saved = service.saveExportPackage(packageWith("Guarded", Domain.LOCATIONS.name()));
		Context.becomeUser("3-4"); // butch: no metadata export privileges in the standard dataset
		try {
			assertThrows(APIAuthenticationException.class, () -> service.getPackageByUuid(saved.getUuid()));
			
			Context.addProxyPrivilege(MetadataExportConstants.GET_PRIVILEGE);
			assertNotNull(service.getPackageByUuid(saved.getUuid()));
			assertEquals(1, service.getCountOfPackages(false));
			assertEquals(1, service.getPackages(false, 0, 10).size());
			assertEquals(0, service.getCountOfBuilds(saved));
			assertThrows(APIAuthenticationException.class,
			    () -> service.saveExportPackage(packageWith("Not allowed", Domain.LOCATIONS.name())));
			assertThrows(APIAuthenticationException.class, () -> service.retireExportPackage(saved, "x"));
			assertThrows(APIAuthenticationException.class, () -> service.saveExportBuild(new ExportBuild()));
			assertThrows(APIAuthenticationException.class, () -> service.getBuildZip(new ExportBuild()));
			assertThrows(APIAuthenticationException.class, () -> service.failStrandedBuilds("x"));
		}
		finally {
			Context.removeProxyPrivilege(MetadataExportConstants.GET_PRIVILEGE);
			authenticate();
		}
	}
	
	@Test
	void getBuilds_pagesNewestFirstWithTheFullCount() {
		ExportPackage saved = service.saveExportPackage(packageWith("Built often", Domain.LOCATIONS.name()));
		for (int version = 1; version <= 3; version++) {
			ExportBuild build = new ExportBuild();
			build.setExportPackage(saved);
			build.setVersion(version);
			build.setExportStatus(ExportStatus.COMPLETED);
			service.saveExportBuild(build);
		}
		
		assertEquals(3, service.getCountOfBuilds(saved));
		assertEquals(Arrays.asList(3, 2), versions(service.getBuilds(saved, 0, 2)));
		assertEquals(Collections.singletonList(1), versions(service.getBuilds(saved, 2, 2)));
	}
	
	@Test
	void failStrandedBuilds_marksActiveBuildsFailedAndLeavesTerminalOnesAlone() {
		ExportPackage saved = service.saveExportPackage(packageWith("Stranded", Domain.LOCATIONS.name()));
		ExportBuild running = new ExportBuild();
		running.setExportPackage(saved);
		running.setVersion(1);
		running.setExportStatus(ExportStatus.RUNNING);
		service.saveExportBuild(running);
		ExportBuild completed = new ExportBuild();
		completed.setExportPackage(saved);
		completed.setVersion(2);
		completed.setExportStatus(ExportStatus.COMPLETED);
		service.saveExportBuild(completed);
		
		int recovered = service.failStrandedBuilds("Interrupted by a server restart");
		
		assertEquals(1, recovered);
		ExportBuild reloaded = service.getBuildByUuid(running.getUuid());
		assertEquals(ExportStatus.FAILED, reloaded.getExportStatus());
		assertEquals("Interrupted by a server restart", reloaded.getErrorMessage());
		assertEquals(ExportStatus.COMPLETED, service.getBuildByUuid(completed.getUuid()).getExportStatus());
	}
	
	@Test
	void retirePackage_fillsTheRetireFieldsViaAop() {
		ExportPackage saved = service.saveExportPackage(packageWith("Old", Domain.LOCATIONS.name()));
		
		service.retireExportPackage(saved, "obsolete");
		
		ExportPackage reloaded = service.getPackageByUuid(saved.getUuid());
		assertTrue(reloaded.getRetired());
		assertEquals("obsolete", reloaded.getRetireReason());
		assertNotNull(reloaded.getRetiredBy());
		assertNotNull(reloaded.getDateRetired());
	}
	
	@Test
	void runBuild_exportsTheScopedLocationsAndZipsThem() throws Exception {
		Location target = Context.getLocationService().getAllLocations().get(0);
		ExportPackage saved = service
		        .saveExportPackage(packageWith("Site A locations", Domain.LOCATIONS.name(), target.getUuid()));
		
		ExportBuild build = new ExportBuild();
		build.setExportPackage(saved);
		build.setVersion(1);
		build.setExportStatus(ExportStatus.QUEUED);
		build = service.saveExportBuild(build);
		
		ExportBuild completed = service.runBuild(build.getUuid());
		
		assertEquals(ExportStatus.COMPLETED, completed.getExportStatus());
		assertNotNull(completed.getDateCompleted());
		assertNotNull(completed.getManifestJson());
		assertTrue(completed.getManifestJson().contains(target.getUuid()));
		
		File zip = service.getBuildZip(completed);
		assertNotNull(zip);
		assertTrue(zip.exists(), "expected " + zip);
		try (ZipFile zipFile = new ZipFile(zip)) {
			assertNotNull(zipFile.getEntry("package.json"), "package.json should sit at the zip root");
			assertNotNull(zipFile.getEntry("configuration/locations/locations.csv"),
			    "the Initializer tree should sit beside it");
		}
	}
	
	@Test
	void runBuild_withNoEntriesExportsEveryRegisteredDomain() throws Exception {
		ExportPackage everything = new ExportPackage();
		everything.setName("Everything");
		everything.setDescription("test");
		ExportPackage saved = service.saveExportPackage(everything);
		
		ExportBuild build = new ExportBuild();
		build.setExportPackage(saved);
		build.setVersion(1);
		build.setExportStatus(ExportStatus.QUEUED);
		build = service.saveExportBuild(build);
		
		ExportBuild completed = service.runBuild(build.getUuid());
		
		assertEquals(ExportStatus.COMPLETED, completed.getExportStatus());
		try (ZipFile zipFile = new ZipFile(service.getBuildZip(completed))) {
			assertNotNull(zipFile.getEntry("configuration/locations/locations.csv"));
			assertNotNull(zipFile.getEntry("configuration/encountertypes/encounterTypes.csv"));
			assertNotNull(zipFile.getEntry("package.json"));
		}
	}
	
	private static List<String> names(List<ExportPackage> packages) {
		return packages.stream().map(ExportPackage::getName).collect(Collectors.toList());
	}
	
	private static List<Integer> versions(List<ExportBuild> builds) {
		return builds.stream().map(ExportBuild::getVersion).collect(Collectors.toList());
	}
	
	private static ExportPackage packageWith(String name, String domain, String... itemUuids) {
		ExportPackage exportPackage = new ExportPackage();
		exportPackage.setName(name);
		exportPackage.setDescription("test package");
		ExportPackageEntry entry = new ExportPackageEntry();
		entry.setDomain(domain);
		entry.getItemUuids().addAll(Arrays.asList(itemUuids));
		exportPackage.getEntries().add(entry);
		return exportPackage;
	}
}
