/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.metadataexport.domain.appointment;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openmrs.Location;
import org.openmrs.OpenmrsObject;
import org.openmrs.api.context.Context;
import org.openmrs.module.appointments.model.AppointmentServiceDefinition;
import org.openmrs.module.appointments.model.AppointmentServiceType;
import org.openmrs.module.appointments.model.Speciality;
import org.openmrs.module.appointments.service.AppointmentServiceDefinitionService;
import org.openmrs.module.appointments.service.SpecialityService;
import org.openmrs.module.initializer.Domain;
import org.openmrs.module.initializer.api.CsvFailingLines;
import org.openmrs.module.initializer.api.CsvParser;
import org.openmrs.module.initializer.api.appt.servicedefinitions.AppointmentServiceDefinitionLineProcessor;
import org.openmrs.module.initializer.api.appt.servicedefinitions.AppointmentServiceDefinitionsCsvParser;
import org.openmrs.module.initializer.api.appt.servicetypes.AppointmentServiceTypeLineProcessor;
import org.openmrs.module.initializer.api.appt.servicetypes.AppointmentServiceTypesCsvParser;
import org.openmrs.module.initializer.api.appt.specialities.SpecialitiesCsvParser;
import org.openmrs.module.initializer.api.appt.specialities.SpecialityLineProcessor;
import org.openmrs.module.metadataexport.export.ExportContext;
import org.openmrs.module.metadataexport.export.ExportLine;
import org.openmrs.test.jupiter.BaseModuleContextSensitiveTest;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Paths;
import java.sql.Time;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppointmentDomainExportersIntegrationTest extends BaseModuleContextSensitiveTest {
	
	private static final String SPECIALITY_UUID = "21ec1632-420f-473c-b380-31ed45214362";
	
	private static final String FULL_UUID = "fc46dedf-5e96-44d4-bd99-bec1d80d15d5";
	
	private static final String BARE_UUID = "6b220700-4ba2-4846-86a7-a2afa5b6f2eb";
	
	private static final String VOIDED_UUID = "762e165a-af27-45fe-ad6e-1fe19db78198";
	
	private static final String TYPE_UUID = "f378bec4-2d0d-4509-a56e-b709e0a53700";
	
	private static final String VOIDED_TYPE_UUID = "4e0f61df-d1f7-4cff-8d69-6264666daf3b";
	
	private static final Time START = Time.valueOf("09:00:00");
	
	private static final Time END = Time.valueOf("17:30:00");
	
	private final SpecialityDomainExporter specialityExporter = new SpecialityDomainExporter();
	
	private final AppointmentServiceDefinitionDomainExporter definitionExporter = new AppointmentServiceDefinitionDomainExporter();
	
	private final AppointmentServiceTypeDomainExporter typeExporter = new AppointmentServiceTypeDomainExporter();
	
	private Location location;
	
	@BeforeEach
	void seedAppointmentMetadata() {
		location = Context.getLocationService().getLocation(1);
		
		Speciality speciality = new Speciality();
		speciality.setUuid(SPECIALITY_UUID);
		speciality.setName("Radiology");
		specialityService().save(speciality);
		
		AppointmentServiceDefinition full = definition(FULL_UUID, "X-Ray");
		full.setDescription("Diagnostic imaging");
		full.setDurationMins(30);
		full.setStartTime(START);
		full.setEndTime(END);
		full.setMaxAppointmentsLimit(15);
		full.setSpeciality(speciality);
		full.setLocation(location);
		full.setColor("#8FBC8F");
		AppointmentServiceType type = type(TYPE_UUID, "Short follow-up", 10, full);
		AppointmentServiceType voidedType = type(VOIDED_TYPE_UUID, "Long follow-up", 45, full);
		voidedType.setVoided(true);
		voidedType.setVoidReason("No longer offered");
		full.setServiceTypes(new LinkedHashSet<>(Arrays.asList(type, voidedType)));
		definitionService().save(full);
		
		definitionService().save(definition(BARE_UUID, "Walk-in"));
		
		AppointmentServiceDefinition voided = definition(VOIDED_UUID, "Dental");
		voided.setStartTime(START);
		voided.setVoided(true);
		voided.setVoidReason("Discontinued");
		definitionService().save(voided);
		Context.flushSession();
	}
	
	@Test
	void getAllInstances_excludeVoidedRowsConsistently() {
		assertEquals(new HashSet<>(Arrays.asList(FULL_UUID, BARE_UUID)), uuidsOf(definitionExporter.getAllInstances()),
		    "voided definitions must not be exported as live rows");
		assertEquals(Collections.singleton(TYPE_UUID), uuidsOf(typeExporter.getAllInstances()),
		    "voided types must not be exported as live rows");
		assertTrue(uuidsOf(specialityExporter.getAllInstances()).contains(SPECIALITY_UUID));
	}
	
	@Test
	void getDependencies_yieldTheReferencedRows() {
		AppointmentServiceDefinition full = reloadDefinition(FULL_UUID);
		AppointmentServiceType type = definitionService().getAppointmentServiceTypeByUuid(TYPE_UUID);
		
		assertEquals(new HashSet<>(Arrays.asList(SPECIALITY_UUID, location.getUuid())),
		    uuidsOf(definitionExporter.getDependencies(full)));
		assertEquals(Collections.singleton(FULL_UUID), uuidsOf(typeExporter.getDependencies(type)));
		
		ExportLine line = new ExportLine();
		new AppointmentServiceDefinitionLineExporter().writeLine(full, line);
		assertEquals(SPECIALITY_UUID, line.get("speciality"));
		assertEquals(location.getUuid(), line.get("location"));
	}
	
	@Test
	void export_thenReimportOntoAFreshTarget(@TempDir File outDir) throws Exception {
		exportAll(outDir);
		purgeAll();
		assertTrue(specialityService().getAllSpecialities().isEmpty(), "the target must start without the rows");
		assertTrue(definitionService().getAllAppointmentServices(true).isEmpty());
		
		replayAllThroughInitializer(outDir);
		
		Speciality speciality = specialityService().getSpecialityByUuid(SPECIALITY_UUID);
		assertNotNull(speciality, "the speciality must come back under its own uuid");
		assertEquals("Radiology", speciality.getName());
		
		AppointmentServiceDefinition full = reloadDefinition(FULL_UUID);
		assertEquals("X-Ray", full.getName());
		assertEquals("Diagnostic imaging", full.getDescription());
		assertEquals(30, full.getDurationMins());
		assertEquals(START.toLocalTime(), full.getStartTime().toLocalTime());
		assertEquals(END.toLocalTime(), full.getEndTime().toLocalTime());
		assertEquals(15, full.getMaxAppointmentsLimit());
		assertEquals(SPECIALITY_UUID, full.getSpeciality().getUuid(),
		    "the speciality reference must resolve, not be dropped");
		assertEquals(location.getUuid(), full.getLocation().getUuid());
		assertEquals("#8FBC8F", full.getColor());
		assertFalse(full.getVoided());
		
		AppointmentServiceDefinition bare = reloadDefinition(BARE_UUID);
		assertEquals("Walk-in", bare.getName());
		assertNull(bare.getDescription(), "a blank cell under a shared header must import as null");
		assertNull(bare.getStartTime());
		assertNull(bare.getSpeciality());
		assertNull(bare.getLocation());
		
		AppointmentServiceType type = definitionService().getAppointmentServiceTypeByUuid(TYPE_UUID);
		assertNotNull(type, "the type must come back under its own uuid");
		assertEquals("Short follow-up", type.getName());
		assertEquals(10, type.getDuration());
		assertEquals(FULL_UUID, type.getAppointmentServiceDefinition().getUuid());
		
		assertEquals(2, definitionService().getAllAppointmentServices(true).size(),
		    "the voided definition is not in the file, so it must not reappear");
		assertNull(definitionService().getAppointmentServiceTypeByUuid(VOIDED_TYPE_UUID),
		    "the voided type is not in the file, so it must not reappear");
	}
	
	@Test
	void export_thenReimportOntoATargetThatAlreadyHasTheRows(@TempDir File outDir) throws Exception {
		exportAll(outDir);
		
		replayAllThroughInitializer(outDir);
		
		assertEquals(1, specialityService().getAllSpecialities().size(),
		    "existing rows are matched by uuid, not duplicated");
		assertEquals(3, definitionService().getAllAppointmentServices(true).size());
		assertEquals("X-Ray", reloadDefinition(FULL_UUID).getName());
		assertEquals(SPECIALITY_UUID, reloadDefinition(FULL_UUID).getSpeciality().getUuid());
		AppointmentServiceDefinition voided = definitionService().getAppointmentServiceByUuid(VOIDED_UUID);
		assertTrue(voided.getVoided(), "the voided definition is not in the file, so the import must leave it alone");
		assertEquals("Dental", voided.getName());
		AppointmentServiceType type = definitionService().getAppointmentServiceTypeByUuid(TYPE_UUID);
		assertEquals("Short follow-up", type.getName());
		assertEquals(FULL_UUID, type.getAppointmentServiceDefinition().getUuid());
	}
	
	private void exportAll(File outDir) throws Exception {
		ExportContext context = new ExportContext(outDir);
		specialityExporter.export(specialityExporter.getAllInstances(), context);
		definitionExporter.export(definitionExporter.getAllInstances(), context);
		typeExporter.export(typeExporter.getAllInstances(), context);
	}
	
	private void replayAllThroughInitializer(File outDir) throws Exception {
		CsvFailingLines failed = replay(outDir, Domain.APPOINTMENT_SPECIALITIES, specialityExporter.fileName(),
		    new SpecialitiesCsvParser(specialityService(), new SpecialityLineProcessor()), 1);
		assertTrue(failed.getFailingLines().isEmpty(), describe(failed));
		Context.flushSession();
		Context.clearSession();
		
		failed = replay(outDir, Domain.APPOINTMENT_SERVICE_DEFINITIONS, definitionExporter.fileName(),
		    new AppointmentServiceDefinitionsCsvParser(definitionService(),
		            new AppointmentServiceDefinitionLineProcessor(specialityService(), Context.getLocationService())),
		    2);
		assertTrue(failed.getFailingLines().isEmpty(), describe(failed));
		Context.flushSession();
		Context.clearSession();
		
		failed = replay(outDir, Domain.APPOINTMENT_SERVICE_TYPES, typeExporter.fileName(),
		    new AppointmentServiceTypesCsvParser(definitionService(),
		            new AppointmentServiceTypeLineProcessor(definitionService())),
		    1);
		assertTrue(failed.getFailingLines().isEmpty(), describe(failed));
		Context.flushSession();
		Context.clearSession();
	}
	
	private static <T extends OpenmrsObject> CsvFailingLines replay(File outDir, Domain domain, String fileName,
	        CsvParser<T, ?> parser, int expectedLines) throws Exception {
		File csv = outDir.toPath().resolve(Paths.get("configuration", domain.getName(), fileName)).toFile();
		assertTrue(csv.exists(), "expected " + csv);
		try (InputStream in = new FileInputStream(csv)) {
			parser.setInputStream(in);
			List<String[]> lines = parser.getLines();
			assertEquals(expectedLines, lines.size(), "only live rows must be in " + fileName);
			return parser.process(lines);
		}
	}
	
	private static AppointmentServiceDefinition definition(String uuid, String name) {
		AppointmentServiceDefinition definition = new AppointmentServiceDefinition();
		definition.setUuid(uuid);
		definition.setName(name);
		return definition;
	}
	
	private static AppointmentServiceType type(String uuid, String name, int duration,
	        AppointmentServiceDefinition definition) {
		AppointmentServiceType type = new AppointmentServiceType();
		type.setUuid(uuid);
		type.setName(name);
		type.setDuration(duration);
		type.setAppointmentServiceDefinition(definition);
		return type;
	}
	
	private static AppointmentServiceDefinition reloadDefinition(String uuid) {
		AppointmentServiceDefinition definition = definitionService().getAppointmentServiceByUuid(uuid);
		assertNotNull(definition, "definition " + uuid + " is not on the target");
		return definition;
	}
	
	private static void purgeAll() {
		SessionFactory sessionFactory = Context.getRegisteredComponent("sessionFactory", SessionFactory.class);
		for (AppointmentServiceDefinition definition : definitionService().getAllAppointmentServices(true)) {
			sessionFactory.getCurrentSession().delete(definition);
		}
		for (Speciality speciality : specialityService().getAllSpecialities()) {
			sessionFactory.getCurrentSession().delete(speciality);
		}
		sessionFactory.getCurrentSession().flush();
		sessionFactory.getCurrentSession().clear();
	}
	
	private static Set<String> uuidsOf(Collection<? extends OpenmrsObject> objects) {
		return objects.stream().map(OpenmrsObject::getUuid).collect(Collectors.toSet());
	}
	
	private static String describe(CsvFailingLines failed) {
		return failed.getErrorDetails().stream()
		        .map(d -> d.getCsvLine().prettyPrint() + " -> " + ExceptionUtils.getRootCauseMessage(d.getException()))
		        .collect(Collectors.joining("\n", "Iniz rejected exported lines:\n", ""));
	}
	
	private static SpecialityService specialityService() {
		return Context.getService(SpecialityService.class);
	}
	
	private static AppointmentServiceDefinitionService definitionService() {
		return Context.getService(AppointmentServiceDefinitionService.class);
	}
}
