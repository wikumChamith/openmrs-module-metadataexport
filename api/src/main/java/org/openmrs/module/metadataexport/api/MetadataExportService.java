/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.metadataexport.api;

import org.openmrs.annotation.Authorized;
import org.openmrs.api.OpenmrsService;
import org.openmrs.module.metadataexport.MetadataExportConstants;
import org.openmrs.module.metadataexport.api.model.ExportBuild;
import org.openmrs.module.metadataexport.api.model.ExportPackage;

import java.io.File;
import java.util.List;

/**
 * Privileges are enforced here (via OpenMRS' AuthorizationAdvice on the service proxy) rather than
 * in the REST layer, so every entry point that reaches the service - REST resources, the download
 * controller, future UIs - gets the same checks. Code running on the module's daemon thread (the
 * job runner's execute step, the activator's startup work) bypasses them because
 * Context.hasPrivilege short-circuits for daemon threads; ExportJobRunner.trigger itself runs on
 * the caller's thread and is checked like any other caller.
 */
public interface MetadataExportService extends OpenmrsService {
	
	@Authorized(MetadataExportConstants.MANAGE_PRIVILEGE)
	ExportPackage saveExportPackage(ExportPackage exportPackage);
	
	@Authorized(MetadataExportConstants.GET_PRIVILEGE)
	ExportPackage getPackageByUuid(String uuid);
	
	@Authorized(MetadataExportConstants.GET_PRIVILEGE)
	List<ExportPackage> getAllPackages(boolean includeRetired);
	
	@Authorized(MetadataExportConstants.GET_PRIVILEGE)
	List<ExportPackage> getPackages(boolean includeRetired, int startIndex, int limit);
	
	@Authorized(MetadataExportConstants.GET_PRIVILEGE)
	long getCountOfPackages(boolean includeRetired);
	
	@Authorized(MetadataExportConstants.MANAGE_PRIVILEGE)
	ExportPackage retireExportPackage(ExportPackage exportPackage, String reason);
	
	@Authorized(MetadataExportConstants.MANAGE_PRIVILEGE)
	ExportBuild saveExportBuild(ExportBuild build);
	
	@Authorized(MetadataExportConstants.GET_PRIVILEGE)
	ExportBuild getBuildByUuid(String uuid);
	
	@Authorized(MetadataExportConstants.GET_PRIVILEGE)
	List<ExportBuild> getBuilds(ExportPackage exportPackage);
	
	@Authorized(MetadataExportConstants.GET_PRIVILEGE)
	List<ExportBuild> getBuilds(ExportPackage exportPackage, int startIndex, int limit);
	
	@Authorized(MetadataExportConstants.GET_PRIVILEGE)
	long getCountOfBuilds(ExportPackage exportPackage);
	
	@Authorized(MetadataExportConstants.GET_PRIVILEGE)
	ExportBuild getLatestBuild(ExportPackage exportPackage);
	
	@Authorized(MetadataExportConstants.MANAGE_PRIVILEGE)
	ExportBuild runBuild(String buildUuid);
	
	@Authorized(MetadataExportConstants.MANAGE_PRIVILEGE)
	int failStrandedBuilds(String reason);
	
	@Authorized(MetadataExportConstants.MANAGE_PRIVILEGE)
	File getBuildZip(ExportBuild build);
}
