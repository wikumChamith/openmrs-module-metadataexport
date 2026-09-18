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
import org.openmrs.OpenmrsObject;
import org.openmrs.module.appointments.model.AppointmentServiceDefinition;
import org.openmrs.module.appointments.model.AppointmentServiceType;
import org.openmrs.module.appointments.model.Speciality;
import org.openmrs.module.initializer.Domain;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppointmentServiceTypeDomainExporterTest {
	
	private final AppointmentServiceTypeDomainExporter exporter = new AppointmentServiceTypeDomainExporter();
	
	@Test
	void ownsTheServiceTypesDomain() {
		assertEquals(Domain.APPOINTMENT_SERVICE_TYPES, exporter.getDomain());
	}
	
	@Test
	void handlesOnlyServiceTypes() {
		assertTrue(exporter.handles(new AppointmentServiceType()));
		assertFalse(exporter.handles(new AppointmentServiceDefinition()));
		assertFalse(exporter.handles(new Speciality()));
	}
	
	@Test
	void getDependencies_isTheParentDefinition() {
		AppointmentServiceDefinition definition = new AppointmentServiceDefinition();
		AppointmentServiceType type = new AppointmentServiceType();
		type.setAppointmentServiceDefinition(definition);
		
		Collection<? extends OpenmrsObject> dependencies = exporter.getDependencies(type);
		
		assertEquals(1, dependencies.size());
		assertTrue(dependencies.contains(definition), "a type row references its definition, so the closure must carry it");
	}
}
