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

import org.openmrs.module.appointments.model.AppointmentServiceType;
import org.openmrs.module.initializer.api.BaseLineProcessor;
import org.openmrs.module.metadataexport.export.BaseLineExporter;
import org.openmrs.module.metadataexport.export.ExportLine;

import static org.openmrs.module.initializer.api.appt.servicetypes.AppointmentServiceTypeLineProcessor.HEADER_SERVICE_DEFINITION;

public class AppointmentServiceTypeLineExporter extends BaseLineExporter<AppointmentServiceType> {
	
	@Override
	public void export(AppointmentServiceType instance, ExportLine line) {
		line.put(BaseLineProcessor.HEADER_UUID, instance.getUuid());
		line.put(BaseLineProcessor.HEADER_NAME, instance.getName());
		line.put(BaseLineProcessor.HEADER_DURATION, instance.getDuration());
		line.put(HEADER_SERVICE_DEFINITION, instance.getAppointmentServiceDefinition().getUuid());
	}
}
