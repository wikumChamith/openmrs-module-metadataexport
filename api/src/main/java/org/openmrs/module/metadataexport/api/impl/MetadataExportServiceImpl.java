/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.metadataexport.api.impl;

import lombok.AllArgsConstructor;
import org.openmrs.OpenmrsObject;
import org.openmrs.api.APIException;
import org.openmrs.api.impl.BaseOpenmrsService;
import org.openmrs.module.metadataexport.api.ExporterService;
import org.openmrs.module.metadataexport.api.MetadataExportService;
import org.openmrs.module.metadataexport.api.db.MetadataExportDao;
import org.openmrs.module.metadataexport.api.model.ExportBuild;
import org.openmrs.module.metadataexport.api.model.ExportPackage;
import org.openmrs.module.metadataexport.api.model.ExportPackageEntry;
import org.openmrs.module.metadataexport.api.model.ExportStatus;
import org.openmrs.module.metadataexport.export.BuildManifest;
import org.openmrs.module.metadataexport.export.DomainExporter;
import org.openmrs.module.metadataexport.export.DomainExporterRegistry;
import org.openmrs.module.metadataexport.export.ZipUtils;
import org.openmrs.module.metadataexport.select.ExportManifest;
import org.openmrs.util.OpenmrsUtil;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@AllArgsConstructor
@Transactional
public class MetadataExportServiceImpl extends BaseOpenmrsService implements MetadataExportService {
	
	private final MetadataExportDao metadataExportDao;
	
	private final DomainExporterRegistry domainExporterRegistry;
	
	private final ExporterService exporterService;
	
	@Override
	public ExportPackage saveExportPackage(ExportPackage exportPackage) {
		for (ExportPackageEntry entry : exportPackage.getEntries()) {
			entry.setExportPackage(exportPackage);
		}
		return metadataExportDao.savePackage(exportPackage);
	}
	
	@Override
	@Transactional(readOnly = true)
	public ExportPackage getPackageByUuid(String uuid) {
		return metadataExportDao.getPackageByUuid(uuid);
	}
	
	@Override
	@Transactional(readOnly = true)
	public List<ExportPackage> getPackages(boolean includeRetired, int startIndex, int limit) {
		return metadataExportDao.getPackages(includeRetired, startIndex, limit);
	}
	
	@Override
	@Transactional(readOnly = true)
	public long getCountOfPackages(boolean includeRetired) {
		return metadataExportDao.getCountOfPackages(includeRetired);
	}
	
	@Override
	public ExportPackage retireExportPackage(ExportPackage exportPackage, String reason) {
		return metadataExportDao.savePackage(exportPackage);
	}
	
	@Override
	public ExportBuild saveExportBuild(ExportBuild build) {
		return metadataExportDao.saveBuild(build);
	}
	
	@Override
	@Transactional(readOnly = true)
	public ExportBuild getBuildByUuid(String uuid) {
		return metadataExportDao.getBuildByUuid(uuid);
	}
	
	@Override
	@Transactional(readOnly = true)
	public List<ExportBuild> getBuilds(ExportPackage exportPackage) {
		return metadataExportDao.getBuilds(exportPackage);
	}
	
	@Override
	@Transactional(readOnly = true)
	public List<ExportBuild> getBuilds(ExportPackage exportPackage, int startIndex, int limit) {
		return metadataExportDao.getBuilds(exportPackage, startIndex, limit);
	}
	
	@Override
	@Transactional(readOnly = true)
	public long getCountOfBuilds(ExportPackage exportPackage) {
		return metadataExportDao.getCountOfBuilds(exportPackage);
	}
	
	@Override
	@Transactional(readOnly = true)
	public ExportBuild getLatestBuild(ExportPackage exportPackage) {
		return metadataExportDao.getLatestBuild(exportPackage);
	}
	
	@Override
	public ExportBuild runBuild(String buildUuid) {
		ExportBuild build = metadataExportDao.getBuildByUuid(buildUuid);
		if (build == null) {
			throw new APIException("No export build with uuid " + buildUuid);
		}
		ExportPackage exportPackage = build.getExportPackage();
		
		List<OpenmrsObject> seeds = new ArrayList<>();
		if (exportPackage.getEntries().isEmpty()) {
			// no entries = every registered domain, like the startup export
			for (DomainExporter<?> exporter : domainExporterRegistry.all()) {
				seeds.addAll(exporter.getAllInstances());
			}
		} else {
			for (ExportPackageEntry entry : exportPackage.getEntries()) {
				DomainExporter<?> exporter = domainExporterRegistry.forDomain(entry.getDomainEnum());
				if (exporter == null) {
					throw new APIException("No exporter registered for domain " + entry.getDomain());
				}
				if (entry.getItemUuids().isEmpty()) {
					seeds.addAll(exporter.getAllInstances());
				} else {
					seeds.addAll(exporter.getInstancesByUuids(entry.getItemUuids()));
				}
			}
		}
		
		File appDataDir = new File(OpenmrsUtil.getApplicationDataDirectory());
		File versionDir = Paths.get(appDataDir.getPath(), "metadataexport", "packages", exportPackage.getUuid(),
		    String.valueOf(build.getVersion())).toFile();
		File contentDir = new File(versionDir, "content");
		try {
			ExportManifest exported = exporterService.exportSeeds(contentDir, seeds);
			
			String manifestJson = BuildManifest.of(exportPackage, build, exported).toJson();
			Files.createDirectories(contentDir.toPath());
			Files.write(new File(contentDir, "package.json").toPath(), manifestJson.getBytes(StandardCharsets.UTF_8));
			
			File zip = new File(versionDir, zipFileName(exportPackage.getName(), build.getVersion()));
			ZipUtils.zipDirectory(contentDir, zip);
			
			build.setExportStatus(ExportStatus.COMPLETED);
			build.setDateCompleted(new Date());
			build.setZipPath(appDataDir.toPath().relativize(zip.toPath()).toString().replace(File.separatorChar, '/'));
			build.setManifestJson(manifestJson);
			return metadataExportDao.saveBuild(build);
		}
		catch (IOException e) {
			throw new APIException("Export of build " + buildUuid + " failed", e);
		}
	}
	
	private static String zipFileName(String packageName, Integer version) {
		String slug = packageName.trim().toLowerCase().replaceAll("[^a-z0-9._-]+", "-");
		return "metadataexport-" + slug + "-v" + version + ".zip";
	}
	
	@Override
	public int failStrandedBuilds(String reason) {
		List<ExportBuild> stranded = metadataExportDao.getActiveBuilds();
		for (ExportBuild build : stranded) {
			build.setExportStatus(ExportStatus.FAILED);
			build.setDateCompleted(new Date());
			build.setErrorMessage(reason);
			metadataExportDao.saveBuild(build);
		}
		return stranded.size();
	}
	
	@Override
	@Transactional(readOnly = true)
	public File getBuildZip(ExportBuild build) {
		if (build.getZipPath() == null) {
			return null;
		}
		return new File(OpenmrsUtil.getApplicationDataDirectory(), build.getZipPath());
	}
}
