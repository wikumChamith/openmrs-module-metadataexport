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

import lombok.extern.slf4j.Slf4j;
import org.openmrs.api.context.Context;
import org.openmrs.module.metadataexport.api.MetadataExportService;
import org.openmrs.module.metadataexport.api.model.ExportBuild;
import org.openmrs.module.metadataexport.api.model.ExportStatus;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.util.Collections;
import java.util.Map;

/**
 * Streams a completed build's zip. Everything else about builds is the {@code ExportBuildResource}
 * REST resource; this stays a plain controller because the REST framework only speaks JSON/XML. Its
 * mapping has one URI variable against the four of the framework's sub-resource pattern, so
 * Spring's pattern comparator routes here first. Privileges are enforced by the service.
 */
@Slf4j
@Controller("metadataexport.ExportBuildController")
@RequestMapping(MetadataExportRestConstants.BASE + "/builds")
public class ExportBuildController {
	
	@GetMapping("/{uuid}/download")
	@ResponseBody
	public ResponseEntity<Map<String, String>> download(@PathVariable String uuid, HttpServletResponse response)
	        throws IOException {
		ExportBuild build = service().getBuildByUuid(uuid);
		if (build == null) {
			return error(HttpStatus.NOT_FOUND, "No export build with uuid " + uuid);
		}
		if (build.getExportStatus() != ExportStatus.COMPLETED) {
			return error(HttpStatus.CONFLICT, "Build is " + build.getExportStatus() + ", not COMPLETED");
		}
		File zip = service().getBuildZip(build);
		if (zip == null) {
			log.error("Metadata Export: build {} is COMPLETED but has no zip path recorded", build.getUuid());
			return error(HttpStatus.INTERNAL_SERVER_ERROR,
			    "The build record is inconsistent (no zip recorded); see the server log");
		}
		if (!zip.exists()) {
			return error(HttpStatus.GONE, "The zip of this build no longer exists on the server");
		}
		response.setContentType("application/zip");
		response.setHeader("Content-Length", String.valueOf(zip.length()));
		response.setHeader("Content-Disposition", "attachment; filename=\"" + zip.getName() + "\"");
		try {
			Files.copy(zip.toPath(), response.getOutputStream());
			response.flushBuffer();
		}
		catch (NoSuchFileException e) {
			// deleted between the exists() check and the copy
			if (!response.isCommitted()) {
				response.reset();
				return error(HttpStatus.GONE, "The zip of this build no longer exists on the server");
			}
			throw e;
		}
		catch (IOException e) {
			if (response.isCommitted()) {
				// almost always the client going away mid-download; nothing more can be sent
				log.info("Metadata Export: download of build {} aborted: {}", build.getUuid(), e.getMessage());
				return null;
			}
			throw e;
		}
		return null;
	}
	
	private static ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
		return ResponseEntity.status(status).body(Collections.singletonMap("error", message));
	}
	
	private static MetadataExportService service() {
		return Context.getService(MetadataExportService.class);
	}
}
