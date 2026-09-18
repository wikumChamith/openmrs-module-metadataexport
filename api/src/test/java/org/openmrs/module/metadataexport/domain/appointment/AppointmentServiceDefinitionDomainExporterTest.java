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
import org.openmrs.OpenmrsObject;
import org.openmrs.module.appointments.model.AppointmentServiceDefinition;
import org.openmrs.module.appointments.model.AppointmentServiceType;
import org.openmrs.module.appointments.model.Speciality;
import org.openmrs.module.initializer.Domain;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppointmentServiceDefinitionDomainExporterTest {
	
	private final AppointmentServiceDefinitionDomainExporter exporter = new AppointmentServiceDefinitionDomainExporter();
	
	@Test
	void ownsTheServiceDefinitionsDomain() {
		assertEquals(Domain.APPOINTMENT_SERVICE_DEFINITIONS, exporter.getDomain());
	}
	
	@Test
	void handlesOnlyServiceDefinitions() {
		assertTrue(exporter.handles(new AppointmentServiceDefinition()));
		assertFalse(exporter.handles(new AppointmentServiceType()));
		assertFalse(exporter.handles(new Speciality()));
		assertFalse(exporter.handles(new Location()));
	}
	
	@Test
	void getDependencies_includesTheSpecialityAndTheLocation() {
		Speciality speciality = new Speciality();
		Location location = new Location();
		AppointmentServiceDefinition definition = new AppointmentServiceDefinition();
		definition.setSpeciality(speciality);
		definition.setLocation(location);
		
		Collection<? extends OpenmrsObject> dependencies = exporter.getDependencies(definition);
		
		assertEquals(2, dependencies.size());
		assertTrue(dependencies.contains(speciality), "the referenced speciality must be pulled into the closure");
		assertTrue(dependencies.contains(location), "the referenced location must be pulled into the closure");
	}
	
	@Test
	void getDependencies_isEmptyWhenNeitherIsSet() {
		assertTrue(exporter.getDependencies(new AppointmentServiceDefinition()).isEmpty());
	}
}
