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

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.hibernate.exception.ConstraintViolationException;
import org.openmrs.api.APIException;
import org.openmrs.api.context.Context;
import org.openmrs.api.context.Daemon;
import org.openmrs.module.metadataexport.MetadataExportDaemonToken;
import org.openmrs.module.metadataexport.api.model.ExportBuild;
import org.openmrs.module.metadataexport.api.model.ExportPackage;
import org.openmrs.module.metadataexport.api.model.ExportStatus;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

/**
 * Triggers export builds and runs them on a daemon thread. Deliberately not transactional: the
 * QUEUED build must be committed before the daemon thread starts, and each status change goes
 * through the transactional {@link MetadataExportService} proxy so polling clients see progress. A
 * build must never be left QUEUED/RUNNING with nothing running it — that would block every future
 * build of its package — so every failure path here ends in a FAILED build or a logged error, and
 * the activator sweeps stranded builds on startup.
 */
@Slf4j
@Component
public class ExportJobRunner {
	
	public ExportBuild trigger(String packageUuid) {
		MetadataExportService service = service();
		ExportPackage exportPackage = service.getPackageByUuid(packageUuid);
		if (exportPackage == null) {
			throw new APIException("No export package with uuid " + packageUuid);
		}
		if (Boolean.TRUE.equals(exportPackage.getRetired())) {
			throw new RetiredPackageException(
			        "Package '" + exportPackage.getName() + "' is retired: " + exportPackage.getRetireReason());
		}
		List<ExportBuild> builds = service.getBuilds(exportPackage);
		for (ExportBuild existing : builds) {
			if (!existing.getExportStatus().isTerminal()) {
				throw new ActiveBuildException("Build v" + existing.getVersion() + " of '" + exportPackage.getName()
				        + "' is already " + existing.getExportStatus());
			}
		}
		ExportBuild build = new ExportBuild();
		build.setExportPackage(exportPackage);
		build.setVersion(builds.isEmpty() ? 1 : builds.get(0).getVersion() + 1);
		build.setExportStatus(ExportStatus.QUEUED);
		final ExportBuild queued;
		try {
			queued = service.saveExportBuild(build);
		}
		catch (RuntimeException e) {
			// two concurrent triggers can both pass the scan above; the unique
			// (package_id, version) constraint catches the loser
			if (ExceptionUtils.indexOfType(e, ConstraintViolationException.class) != -1) {
				throw new ActiveBuildException(
				        "A build of '" + exportPackage.getName() + "' was just triggered concurrently", e);
			}
			throw e;
		}
		
		try {
			Daemon.runInDaemonThreadWithoutResult(() -> execute(queued.getUuid()), MetadataExportDaemonToken.get());
		}
		catch (Exception e) {
			failBuild(queued.getUuid(),
			    "Could not start the export daemon thread: " + ExceptionUtils.getRootCauseMessage(e));
			throw new APIException("Could not start the export daemon thread for build " + queued.getUuid(), e);
		}
		return queued;
	}
	
	// package-private so context-sensitive tests can run a build synchronously
	void execute(String buildUuid) {
		try {
			MetadataExportService service = service();
			ExportBuild build = service.getBuildByUuid(buildUuid);
			build.setExportStatus(ExportStatus.RUNNING);
			build.setDateStarted(new Date());
			service.saveExportBuild(build);
			
			service.runBuild(buildUuid);
		}
		catch (Throwable t) {
			log.error("Metadata Export: build {} failed", buildUuid, t);
			failBuild(buildUuid, ExceptionUtils.getRootCauseMessage(t));
			if (t instanceof Error) {
				throw (Error) t;
			}
		}
	}
	
	private void failBuild(String buildUuid, String errorMessage) {
		try {
			MetadataExportService service = service();
			ExportBuild build = service.getBuildByUuid(buildUuid);
			if (build == null) {
				log.error("Metadata Export: cannot mark unknown build {} as FAILED", buildUuid);
				return;
			}
			build.setExportStatus(ExportStatus.FAILED);
			build.setDateCompleted(new Date());
			build.setErrorMessage(errorMessage);
			service.saveExportBuild(build);
		}
		catch (Exception e) {
			log.error("Metadata Export: build {} failed AND could not be marked FAILED; it will block future builds"
			        + " of its package until the module restarts",
			    buildUuid, e);
		}
	}
	
	private MetadataExportService service() {
		return Context.getService(MetadataExportService.class);
	}
}
