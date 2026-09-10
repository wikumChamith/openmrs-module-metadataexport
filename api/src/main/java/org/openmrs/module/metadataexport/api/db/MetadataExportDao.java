/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.metadataexport.api.db;

import org.openmrs.module.metadataexport.api.model.ExportBuild;
import org.openmrs.module.metadataexport.api.model.ExportPackage;

import java.util.List;

public interface MetadataExportDao {
	
	ExportPackage savePackage(ExportPackage exportPackage);
	
	ExportPackage getPackageByUuid(String uuid);
	
	ExportPackage getPackageByName(String name);
	
	List<ExportPackage> getAllPackages(boolean includeRetired);
	
	List<ExportPackage> getPackages(boolean includeRetired, int startIndex, int limit);
	
	long getCountOfPackages(boolean includeRetired);
	
	ExportBuild saveBuild(ExportBuild exportBuild);
	
	ExportBuild getBuildByUuid(String uuid);
	
	List<ExportBuild> getBuilds(ExportPackage exportPackage);
	
	List<ExportBuild> getBuilds(ExportPackage exportPackage, int startIndex, int limit);
	
	long getCountOfBuilds(ExportPackage exportPackage);
	
	ExportBuild getLatestBuild(ExportPackage exportPackage);
	
	List<ExportBuild> getActiveBuilds();
	
}
