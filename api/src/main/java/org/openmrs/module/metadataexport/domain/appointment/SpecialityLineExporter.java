package org.openmrs.module.metadataexport.domain.appointment;

import org.openmrs.module.appointments.model.Speciality;
import org.openmrs.module.initializer.api.BaseLineProcessor;
import org.openmrs.module.metadataexport.export.BaseLineExporter;
import org.openmrs.module.metadataexport.export.ExportLine;

public class SpecialityLineExporter extends BaseLineExporter<Speciality> {
	@Override
	public void export(Speciality instance, ExportLine line) {
		line.put(BaseLineProcessor.HEADER_UUID, instance.getUuid());
		line.put(BaseLineProcessor.HEADER_NAME, instance.getName());
	}
}
