/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.metadataexport.domain.queue;

import lombok.extern.slf4j.Slf4j;
import org.openmrs.OpenmrsObject;
import org.openmrs.module.initializer.api.BaseLineProcessor;
import org.openmrs.module.metadataexport.export.ExportLine;
import org.openmrs.module.metadataexport.export.MetadataLineExporter;
import org.openmrs.module.queue.model.Queue;

/**
 * Inverse of Initializer's {@code QueueLineProcessor.fill(...)}. Its domain-specific header
 * constants are {@code protected}, so they are repeated here.
 */
@Slf4j
public class QueueLineExporter extends MetadataLineExporter<Queue> {
	
	public final static String HEADER_SERVICE = "service";
	
	public final static String HEADER_STATUS_CONCEPT_SET = "status concept set";
	
	public final static String HEADER_PRIORITY_CONCEPT_SET = "priority concept set";
	
	public final static String HEADER_LOCATION = "location";
	
	@Override
	public void export(Queue queue, ExportLine line) {
		line.put(BaseLineProcessor.HEADER_NAME, queue.getName());
		line.put(BaseLineProcessor.HEADER_DESC, queue.getDescription());
		
		// Service and location are mandatory on a queue; a null here is a corrupt source row, and the
		// queue module's validator will reject it on import, so say so where the export happens.
		putMandatoryReference(line, HEADER_SERVICE, queue.getService(), queue.getUuid());
		putMandatoryReference(line, HEADER_LOCATION, queue.getLocation(), queue.getUuid());
		
		putReference(line, HEADER_STATUS_CONCEPT_SET, queue.getStatusConceptSet());
		putReference(line, HEADER_PRIORITY_CONCEPT_SET, queue.getPriorityConceptSet());
	}
	
	private static void putMandatoryReference(ExportLine line, String header, OpenmrsObject reference, String queueUuid) {
		if (reference == null) {
			log.warn("Queues: queue {} has no {}; the queue module rejects this row on import", queueUuid, header);
		}
		putReference(line, header, reference);
	}
	
	private static void putReference(ExportLine line, String header, OpenmrsObject reference) {
		if (reference != null) {
			line.put(header, reference.getUuid());
		}
	}
}
