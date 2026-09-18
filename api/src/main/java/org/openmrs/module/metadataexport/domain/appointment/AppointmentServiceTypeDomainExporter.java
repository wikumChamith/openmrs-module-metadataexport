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

import org.hibernate.SessionFactory;
import org.openmrs.OpenmrsObject;
import org.openmrs.annotation.OpenmrsProfile;
import org.openmrs.api.context.Context;
import org.openmrs.module.appointments.model.AppointmentServiceType;
import org.openmrs.module.initializer.Domain;
import org.openmrs.module.metadataexport.export.BaseLineExporter;
import org.openmrs.module.metadataexport.export.CsvDomainExporter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Component
@OpenmrsProfile(modules = "appointments:1.2.1 - 9.*")
public class AppointmentServiceTypeDomainExporter extends CsvDomainExporter<AppointmentServiceType> {
	
	@Override
	protected List<BaseLineExporter<AppointmentServiceType>> chain() {
		return Collections.singletonList(new AppointmentServiceTypeLineExporter());
	}
	
	@Override
	protected String fileName() {
		return "appointmentServiceTypes.csv";
	}
	
	@Override
	public Domain getDomain() {
		return Domain.APPOINTMENT_SERVICE_TYPES;
	}
	
	@Override
	public boolean handles(OpenmrsObject instance) {
		return instance instanceof AppointmentServiceType;
	}
	
	@Override
	@SuppressWarnings("unchecked")
	public Collection<AppointmentServiceType> getAllInstances() {
		SessionFactory sessionFactory = Context.getRegisteredComponent("sessionFactory", SessionFactory.class);
		return sessionFactory.getCurrentSession().createQuery("from AppointmentServiceType where voided = false").list();
	}
	
	@Override
	public Collection<? extends OpenmrsObject> getDependencies(AppointmentServiceType instance) {
		List<OpenmrsObject> dependencies = new ArrayList<>();
		dependencies.add(instance.getAppointmentServiceDefinition());
		return dependencies;
	}
}
