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

import org.junit.jupiter.api.Test;
import org.openmrs.module.appointments.model.AppointmentServiceDefinition;
import org.openmrs.module.appointments.model.AppointmentServiceType;
import org.openmrs.module.metadataexport.export.ExportLine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class AppointmentServiceTypeLineExporterTest {
	
	private static final String TYPE_UUID = "f378bec4-2d0d-4509-a56e-b709e0a53700";
	
	private static final String DEFINITION_UUID = "fc46dedf-5e96-44d4-bd99-bec1d80d15d5";
	
	@Test
	void exportsUuidNameDurationAndTheDefinitionUuid() {
		AppointmentServiceDefinition definition = new AppointmentServiceDefinition();
		definition.setUuid(DEFINITION_UUID);
		definition.setName("X-Ray");
		
		AppointmentServiceType type = new AppointmentServiceType();
		type.setUuid(TYPE_UUID);
		type.setName("Short follow-up");
		type.setDuration(10);
		type.setAppointmentServiceDefinition(definition);
		
		ExportLine line = new ExportLine();
		new AppointmentServiceTypeLineExporter().writeLine(type, line);
		
		assertEquals(TYPE_UUID, line.get("uuid"), "Iniz requires a uuid on every service type row");
		assertEquals("Short follow-up", line.get("name"));
		assertEquals("10", line.get("duration"));
		assertEquals(DEFINITION_UUID, line.get("service definition"), "the parent is referenced by uuid");
		assertNull(line.get("void/retire"));
	}
	
	@Test
	void omitsDurationWhenTheTypeHasNone() {
		AppointmentServiceDefinition definition = new AppointmentServiceDefinition();
		definition.setUuid(DEFINITION_UUID);
		
		AppointmentServiceType type = new AppointmentServiceType();
		type.setUuid(TYPE_UUID);
		type.setName("Default");
		type.setAppointmentServiceDefinition(definition);
		
		ExportLine line = new ExportLine();
		new AppointmentServiceTypeLineExporter().writeLine(type, line);
		
		assertFalse(line.containsHeader("duration"));
		assertEquals(DEFINITION_UUID, line.get("service definition"));
	}
}
