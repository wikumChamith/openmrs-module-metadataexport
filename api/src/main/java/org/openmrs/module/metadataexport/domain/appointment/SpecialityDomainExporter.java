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

import org.openmrs.OpenmrsObject;
import org.openmrs.annotation.OpenmrsProfile;
import org.openmrs.api.context.Context;
import org.openmrs.module.appointments.model.Speciality;
import org.openmrs.module.appointments.service.SpecialityService;
import org.openmrs.module.initializer.Domain;
import org.openmrs.module.metadataexport.export.BaseLineExporter;
import org.openmrs.module.metadataexport.export.CsvDomainExporter;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Component
@OpenmrsProfile(modules = "appointments:1.2.1 - 9.*")
public class SpecialityDomainExporter extends CsvDomainExporter<Speciality> {
	
	@Override
	protected List<BaseLineExporter<Speciality>> chain() {
		return Collections.singletonList(new SpecialityLineExporter());
	}
	
	@Override
	protected String fileName() {
		return "specialties.csv";
	}
	
	@Override
	public Domain getDomain() {
		return Domain.APPOINTMENT_SPECIALITIES;
	}
	
	@Override
	public boolean handles(OpenmrsObject instance) {
		return instance instanceof Speciality;
	}
	
	@Override
	public Collection<Speciality> getAllInstances() {
		return Context.getService(SpecialityService.class).getAllSpecialities();
	}
	
	@Override
	public Collection<? extends OpenmrsObject> getDependencies(Speciality instance) {
		return Collections.emptyList();
	}
}
