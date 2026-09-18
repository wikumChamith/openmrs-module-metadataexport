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
import org.openmrs.Location;
import org.openmrs.module.appointments.model.AppointmentServiceDefinition;
import org.openmrs.module.appointments.model.Speciality;
import org.openmrs.module.metadataexport.export.ExportLine;

import java.sql.Time;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class AppointmentServiceDefinitionLineExporterTest {
	
	private static final String DEFINITION_UUID = "fc46dedf-5e96-44d4-bd99-bec1d80d15d5";
	
	private static final String SPECIALITY_UUID = "21ec1632-420f-473c-b380-31ed45214362";
	
	private static final String LOCATION_UUID = "9356400c-a5a2-4532-8f2b-2361b3446eb8";
	
	@Test
	void exportsAllColumnsOfALiveDefinition() {
		Speciality speciality = new Speciality();
		speciality.setUuid(SPECIALITY_UUID);
		speciality.setName("Radiology");
		
		Location location = new Location();
		location.setUuid(LOCATION_UUID);
		location.setName("OPD1");
		
		AppointmentServiceDefinition definition = new AppointmentServiceDefinition();
		definition.setUuid(DEFINITION_UUID);
		definition.setName("X-Ray");
		definition.setDescription("Diagnostic imaging");
		definition.setDurationMins(30);
		definition.setStartTime(Time.valueOf("09:00:00"));
		definition.setEndTime(Time.valueOf("17:30:00"));
		definition.setMaxAppointmentsLimit(15);
		definition.setSpeciality(speciality);
		definition.setLocation(location);
		definition.setColor("#8FBC8F");
		
		ExportLine line = new ExportLine();
		new AppointmentServiceDefinitionLineExporter().writeLine(definition, line);
		
		assertEquals(DEFINITION_UUID, line.get("uuid"));
		assertEquals("X-Ray", line.get("name"));
		assertEquals("Diagnostic imaging", line.get("description"));
		assertEquals("30", line.get("duration"));
		assertEquals("09:00", line.get("start time"));
		assertEquals("17:30", line.get("end time"));
		assertEquals("15", line.get("max load"));
		assertEquals(SPECIALITY_UUID, line.get("speciality"), "references are written by uuid");
		assertEquals(LOCATION_UUID, line.get("location"), "references are written by uuid, not by name");
		assertEquals("#8FBC8F", line.get("label colour"));
		assertNull(line.get("void/retire"));
	}
	
	@Test
	void omitsAbsentOptionalColumns() {
		AppointmentServiceDefinition definition = new AppointmentServiceDefinition();
		definition.setUuid(DEFINITION_UUID);
		definition.setName("Walk-in");
		
		ExportLine line = new ExportLine();
		new AppointmentServiceDefinitionLineExporter().writeLine(definition, line);
		
		assertEquals(DEFINITION_UUID, line.get("uuid"));
		assertEquals("Walk-in", line.get("name"));
		for (String optional : new String[] { "description", "duration", "start time", "end time", "max load", "speciality",
		        "location", "label colour" }) {
			assertFalse(line.containsHeader(optional), optional + " must be left out when the definition has none");
		}
	}
	
	@Test
	void writesTimesInTheHourMinuteFormatInitializerParses() {
		AppointmentServiceDefinition definition = new AppointmentServiceDefinition();
		definition.setUuid(DEFINITION_UUID);
		definition.setName("Surgery");
		definition.setStartTime(Time.valueOf("08:05:59"));
		definition.setEndTime(Time.valueOf("23:00:00"));
		
		ExportLine line = new ExportLine();
		new AppointmentServiceDefinitionLineExporter().writeLine(definition, line);
		
		assertEquals("08:05", line.get("start time"), "seconds are dropped; Iniz has no way to carry them");
		assertEquals("23:00", line.get("end time"));
		assertDoesNotThrow(() -> Time.valueOf(line.get("start time") + ":00"));
		assertDoesNotThrow(() -> Time.valueOf(line.get("end time") + ":00"));
	}
}
