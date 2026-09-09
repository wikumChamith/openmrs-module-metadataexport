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

import lombok.extern.slf4j.Slf4j;
import org.openmrs.ProviderRole;
import org.openmrs.api.context.Context;
import org.openmrs.module.initializer.api.BaseLineProcessor;
import org.openmrs.module.initializer.api.systemtasks.SystemTasksLineProcessor;
import org.openmrs.module.metadataexport.export.ExportLine;
import org.openmrs.module.metadataexport.export.MetadataLineExporter;
import org.openmrs.module.tasks.SystemTask;

@Slf4j
public class SystemTaskLineExporter extends MetadataLineExporter<SystemTask> {
	
	@Override
	public void export(SystemTask instance, ExportLine line) {
		line.put(BaseLineProcessor.HEADER_NAME, instance.getName());
		line.put(SystemTasksLineProcessor.HEADER_TITLE, instance.getTitle());
		line.put(BaseLineProcessor.HEADER_DESC, instance.getDescription());
		line.put(SystemTasksLineProcessor.HEADER_RATIONALE, instance.getRationale());
		
		line.put(SystemTasksLineProcessor.HEADER_PRIORITY, instance.getPriority());
		
		ProviderRole providerRole = resolveAssignee(instance);
		if (providerRole != null) {
			line.put(SystemTasksLineProcessor.HEADER_DEFAULT_ASSIGNEE_ROLE, providerRole.getUuid());
		}
	}
	
	@Override
	protected void writeRetiredDiscriminators(SystemTask instance, ExportLine line) {
		export(instance, line);
	}
	
	static ProviderRole resolveAssignee(SystemTask task) {
		Integer providerRoleId = task.getDefaultAssigneeProviderRoleId();
		if (providerRoleId == null) {
			return null;
		}
		ProviderRole providerRole = Context.getProviderService().getProviderRole(providerRoleId);
		if (providerRole == null) {
			log.warn("System Tasks: skipping default assignee role of system task {} — provider role id {} does not"
			        + " exist, so the task will import unassigned",
			    task.getUuid(), providerRoleId);
		}
		return providerRole;
	}
}
