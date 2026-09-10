/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module;

import org.openmrs.module.metadataexport.MetadataExportActivator;
import org.openmrs.module.metadataexport.MetadataExportConstants;
import org.openmrs.module.metadataexport.MetadataExportDaemonToken;

/**
 * Lives in {@code org.openmrs.module} because {@link ModuleFactory#passDaemonToken(Module)} - the
 * only way to mint a {@link DaemonToken} the {@code Daemon} class accepts - is package-private. In
 * production the module framework calls it during startup; context-sensitive tests never start the
 * module, so without this the job runner cannot launch its daemon thread.
 */
public final class MetadataExportDaemonTokenTestSupport {
	
	private MetadataExportDaemonTokenTestSupport() {
	}
	
	public static void ensureDaemonToken() {
		if (MetadataExportDaemonToken.get() != null && ModuleFactory.isTokenValid(MetadataExportDaemonToken.get())) {
			return;
		}
		Module module = new Module(MetadataExportConstants.MODULE_ID);
		module.setModuleId(MetadataExportConstants.MODULE_ID);
		module.setModuleActivator(new MetadataExportActivator());
		ModuleFactory.passDaemonToken(module);
	}
}
