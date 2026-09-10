/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.metadataexport.web.resource;

import org.openmrs.module.webservices.rest.web.response.ResponseException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 409 for a request that is well-formed but clashes with current state: triggering a build of a
 * retired package, or while another build of the same package is still QUEUED/RUNNING. The REST
 * module's base controller turns any {@link ResponseStatus}-annotated {@link ResponseException}
 * into its standard error body with this status.
 */
@ResponseStatus(value = HttpStatus.CONFLICT)
public class ConflictException extends ResponseException {
	
	private static final long serialVersionUID = 1L;
	
	public ConflictException(String message) {
		super(message);
	}
	
	public ConflictException(String message, Throwable cause) {
		super(message, cause);
	}
}
