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
import org.openmrs.module.appointments.model.Speciality;
import org.openmrs.module.initializer.Domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpecialityDomainExporterTest {
	
	private final SpecialityDomainExporter exporter = new SpecialityDomainExporter();
	
	@Test
	void ownsTheSpecialitiesDomain() {
		assertEquals(Domain.APPOINTMENT_SPECIALITIES, exporter.getDomain());
	}
	
	@Test
	void handlesOnlySpecialities() {
		assertTrue(exporter.handles(new Speciality()));
		assertFalse(exporter.handles(new AppointmentServiceDefinition()));
	}
	
	@Test
	void getDependencies_isEmpty() {
		assertTrue(exporter.getDependencies(new Speciality()).isEmpty());
	}
}
