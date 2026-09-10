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
import org.openmrs.api.APIAuthenticationException;
import org.openmrs.api.context.Context;
import org.openmrs.api.context.ContextAuthenticationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Collections;
import java.util.Map;

/**
 * Error handling for the two plain Spring controllers only (the zip download and the domain list),
 * scoped by type so it never touches {@code MetadataExportRestController}, whose REST-module base
 * class carries its own handlers. Authentication failures become 401/403; anything else these
 * controllers can throw is a server fault, so it is a logged 500. Extends
 * {@link ResponseEntityExceptionHandler} so Spring's request-shape exceptions keep their standard
 * 4xx statuses instead of falling into the Exception catch-all below.
 */
@Slf4j
@RestControllerAdvice(assignableTypes = { ExportBuildController.class, ExportDomainController.class })
public class MetadataExportControllerAdvice extends ResponseEntityExceptionHandler {
	
	@ExceptionHandler({ APIAuthenticationException.class, ContextAuthenticationException.class })
	public ResponseEntity<Map<String, String>> handleAuthentication(RuntimeException e) {
		HttpStatus status = Context.isAuthenticated() ? HttpStatus.FORBIDDEN : HttpStatus.UNAUTHORIZED;
		return ResponseEntity.status(status).body(Collections.singletonMap("error", e.getMessage()));
	}
	
	@ExceptionHandler(Exception.class)
	public ResponseEntity<Map<String, String>> handleUnexpected(Exception e) {
		log.error("Metadata Export: unexpected error handling a REST request", e);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
		        .body(Collections.singletonMap("error", "An unexpected error occurred; see the server log"));
	}
}
