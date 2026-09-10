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

import org.openmrs.module.webservices.rest.web.RequestContext;
import org.openmrs.module.webservices.rest.web.response.IllegalRequestException;

/**
 * The REST module validates {@code limit} (1..absolute max) but passes any integer
 * {@code startIndex} through; a negative one would reach Hibernate and surface as a 500. Reject it
 * as a 400 here.
 */
final class RestPaging {
	
	private RestPaging() {
	}
	
	static int startIndex(RequestContext context) {
		Integer startIndex = context.getStartIndex();
		if (startIndex == null) {
			return 0;
		}
		if (startIndex < 0) {
			throw new IllegalRequestException("startIndex must be 0 or greater, got " + startIndex);
		}
		return startIndex;
	}
	
	static boolean hasMore(int startIndex, int pageSize, long total) {
		return startIndex + pageSize < total;
	}
}
