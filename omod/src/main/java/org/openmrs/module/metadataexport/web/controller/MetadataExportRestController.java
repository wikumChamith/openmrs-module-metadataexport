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

import org.openmrs.module.webservices.rest.web.v1_0.controller.MainResourceController;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Mounts the REST module's generic resource controller under this module's namespace, so
 * {@code /ws/rest/v1/metadataexport/{resource}[/{uuid}]} resolves resources named
 * {@code v1/metadataexport/...}. This is the REST module's convention for module-owned resources:
 * the namespace is not inferred from the URL, the module has to claim it. The literal
 * {@code metadataexport} segment leaves these mappings with fewer URI variables than the
 * framework's generic resource and sub-resource patterns, so Spring's pattern comparator prefers
 * them.
 */
@Controller("metadataexport.MetadataExportRestController")
@RequestMapping(MetadataExportRestConstants.BASE)
public class MetadataExportRestController extends MainResourceController {
	
	@Override
	public String getNamespace() {
		return MetadataExportRestConstants.NAMESPACE;
	}
}
