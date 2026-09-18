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
import org.openmrs.module.appointments.model.Speciality;
import org.openmrs.module.metadataexport.export.ExportLine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SpecialityLineExporterTest {
	
	@Test
	void exportsUuidAndName() {
		Speciality speciality = new Speciality();
		speciality.setUuid("21ec1632-420f-473c-b380-31ed45214362");
		speciality.setName("Radiology");
		
		ExportLine line = new ExportLine();
		new SpecialityLineExporter().writeLine(speciality, line);
		
		assertEquals("21ec1632-420f-473c-b380-31ed45214362", line.get("uuid"),
		    "definitions reference their speciality by uuid, so the speciality row must carry it");
		assertEquals("Radiology", line.get("name"));
		assertNull(line.get("void/retire"));
	}
}
