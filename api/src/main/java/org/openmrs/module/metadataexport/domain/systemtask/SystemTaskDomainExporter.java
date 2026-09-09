/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.metadataexport.domain.systemtask;

import org.openmrs.OpenmrsObject;
import org.openmrs.ProviderRole;
import org.openmrs.annotation.OpenmrsProfile;
import org.openmrs.api.context.Context;
import org.openmrs.module.initializer.Domain;
import org.openmrs.module.metadataexport.export.BaseLineExporter;
import org.openmrs.module.metadataexport.export.CsvDomainExporter;
import org.openmrs.module.tasks.SystemTask;
import org.openmrs.module.tasks.api.TasksService;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Component
@OpenmrsProfile(modules = "tasks:1.0.0 - 9.*")
public class SystemTaskDomainExporter extends CsvDomainExporter<SystemTask> {
	
	@Override
	protected List<BaseLineExporter<SystemTask>> chain() {
		return Collections.singletonList(new SystemTaskLineExporter());
	}
	
	@Override
	protected String fileName() {
		return "systemTasks.csv";
	}
	
	@Override
	public Domain getDomain() {
		return Domain.SYSTEM_TASKS;
	}
	
	@Override
	public boolean handles(OpenmrsObject instance) {
		return instance instanceof SystemTask;
	}
	
	@Override
	public Collection<SystemTask> getAllInstances() {
		return Context.getService(TasksService.class).getAllSystemTasks(true);
	}
	
	@Override
	public Collection<? extends OpenmrsObject> getDependencies(SystemTask instance) {
		// We currently don't have a ProviderRoleExporter in the module. This is because Initializer still only
		// supports the ProviderRole from the providermanagement module, whereas on core 2.8+ provider roles have
		// moved into core. Until an exporter exists, Selector drops the role returned here because no registered
		// domain owns it. Keeping this here for future sake: it is returned anyway so the closure starts working
		// the moment a provider roles domain is added.
		ProviderRole role = SystemTaskLineExporter.resolveAssignee(instance);
		return role == null ? Collections.emptyList() : Collections.singletonList(role);
	}
}
