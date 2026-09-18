package org.openmrs.module.metadataexport.domain.appointment;

import org.openmrs.Location;
import org.openmrs.OpenmrsObject;
import org.openmrs.annotation.OpenmrsProfile;
import org.openmrs.api.context.Context;
import org.openmrs.module.appointments.model.AppointmentServiceDefinition;
import org.openmrs.module.appointments.model.Speciality;
import org.openmrs.module.appointments.service.AppointmentServiceDefinitionService;
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
public class AppointmentServiceDefinitionDomainExporter extends CsvDomainExporter<AppointmentServiceDefinition> {
	@Override
	protected List<BaseLineExporter<AppointmentServiceDefinition>> chain() {
		return Collections.singletonList(new AppointmentServiceDefinitionLineExporter());
	}

	@Override
	protected String fileName() {
		return "appointmentServiceDefinition.csv";
	}

	@Override
	public Domain getDomain() {
		return Domain.APPOINTMENT_SERVICE_DEFINITIONS;
	}

	@Override
	public boolean handles(OpenmrsObject instance) {
		return instance instanceof AppointmentServiceDefinition;
	}

	@Override
	public Collection<AppointmentServiceDefinition> getAllInstances() {
		return Context.getService(AppointmentServiceDefinitionService.class).getAllAppointmentServices(true);
	}

	@Override
	public Collection<? extends OpenmrsObject> getDependencies(AppointmentServiceDefinition instance) {
		List<OpenmrsObject> dependencies = new ArrayList<>();

		Speciality speciality = instance.getSpeciality();
		if (speciality != null) {
			dependencies.add(speciality);
		}

		Location location = instance.getLocation();
		if (location != null) {
			dependencies.add(location);
		}
		return dependencies;
	}
}
