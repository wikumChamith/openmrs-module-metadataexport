/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.metadataexport.web.controller;

import lombok.AllArgsConstructor;
import org.openmrs.api.context.Context;
import org.openmrs.module.metadataexport.MetadataExportConstants;
import org.openmrs.module.metadataexport.export.DomainExporter;
import org.openmrs.module.metadataexport.export.DomainExporterRegistry;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Controller("metadataexport.ExportDomainController")
@RequestMapping(MetadataExportRestConstants.BASE + "/domains")
@AllArgsConstructor
public class ExportDomainController {
	
	private final DomainExporterRegistry domainExporterRegistry;
	
	@GetMapping
	@ResponseBody
	public List<String> listDomains() {
		Context.requirePrivilege(MetadataExportConstants.GET_PRIVILEGE);
		List<String> domains = new ArrayList<>();
		for (DomainExporter<?> exporter : domainExporterRegistry.all()) {
			domains.add(exporter.getDomain().name());
		}
		Collections.sort(domains);
		return domains;
	}
}
