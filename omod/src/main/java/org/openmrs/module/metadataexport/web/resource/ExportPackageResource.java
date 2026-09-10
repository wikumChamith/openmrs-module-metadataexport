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

import org.openmrs.api.context.Context;
import org.openmrs.module.metadataexport.api.MetadataExportService;
import org.openmrs.module.metadataexport.api.model.ExportBuild;
import org.openmrs.module.metadataexport.api.model.ExportPackage;
import org.openmrs.module.metadataexport.api.model.ExportPackageEntry;
import org.openmrs.module.metadataexport.web.controller.MetadataExportRestConstants;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.openmrs.module.webservices.rest.web.RequestContext;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.module.webservices.rest.web.annotation.PropertyGetter;
import org.openmrs.module.webservices.rest.web.annotation.PropertySetter;
import org.openmrs.module.webservices.rest.web.annotation.Resource;
import org.openmrs.module.webservices.rest.web.representation.DefaultRepresentation;
import org.openmrs.module.webservices.rest.web.representation.FullRepresentation;
import org.openmrs.module.webservices.rest.web.representation.Representation;
import org.openmrs.module.webservices.rest.web.resource.api.PageableResult;
import org.openmrs.module.webservices.rest.web.resource.impl.AlreadyPaged;
import org.openmrs.module.webservices.rest.web.resource.impl.DelegatingResourceDescription;
import org.openmrs.module.webservices.rest.web.resource.impl.MetadataDelegatingCrudResource;
import org.openmrs.module.webservices.rest.web.response.ConversionException;
import org.openmrs.module.webservices.rest.web.response.ResourceDoesNotSupportOperationException;
import org.openmrs.module.webservices.rest.web.response.ResponseException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * {@code /ws/rest/v1/metadataexport/packages}. A package is OpenMRS metadata (name, description,
 * retire) plus a list of {@code entries}, each {@code {domain, itemUuids}}; an empty list means
 * "every registered domain". Listing is paged by the REST module's standard
 * {@code startIndex}/{@code limit}, with {@code includeAll=true} to include retired packages.
 * DELETE retires; purge is not supported.
 */
@Resource(name = MetadataExportRestConstants.NAMESPACE
        + "/packages", supportedClass = ExportPackage.class, supportedOpenmrsVersions = {
                MetadataExportRestConstants.SUPPORTED_OPENMRS_VERSIONS })
public class ExportPackageResource extends MetadataDelegatingCrudResource<ExportPackage> {
	
	private static final Set<String> ENTRY_KEYS = new LinkedHashSet<>(Arrays.asList("domain", "itemUuids"));
	
	@Override
	public ExportPackage getByUniqueId(String uuid) {
		return service().getPackageByUuid(uuid);
	}
	
	@Override
	public ExportPackage newDelegate() {
		return new ExportPackage();
	}
	
	@Override
	public ExportPackage save(ExportPackage exportPackage) {
		return service().saveExportPackage(exportPackage);
	}
	
	@Override
	public void delete(ExportPackage exportPackage, String reason, RequestContext context) throws ResponseException {
		service().retireExportPackage(exportPackage, reason);
	}
	
	@Override
	public void purge(ExportPackage exportPackage, RequestContext context) throws ResponseException {
		throw new ResourceDoesNotSupportOperationException("Packages can only be retired, not purged");
	}
	
	@Override
	protected PageableResult doGetAll(RequestContext context) throws ResponseException {
		boolean includeRetired = Boolean.TRUE.equals(context.getIncludeAll());
		int startIndex = RestPaging.startIndex(context);
		List<ExportPackage> page = service().getPackages(includeRetired, startIndex, context.getLimit());
		long total = service().getCountOfPackages(includeRetired);
		return new AlreadyPaged<>(context, page, RestPaging.hasMore(startIndex, page.size(), total), total);
	}
	
	@Override
	public DelegatingResourceDescription getRepresentationDescription(Representation rep) {
		if (rep instanceof DefaultRepresentation) {
			DelegatingResourceDescription description = new DelegatingResourceDescription();
			description.addProperty("uuid");
			description.addProperty("display");
			description.addProperty("name");
			description.addProperty("description");
			description.addProperty("retired");
			description.addProperty("entries");
			description.addProperty("latestBuild", Representation.DEFAULT);
			description.addSelfLink();
			description.addLink("full", ".?v=" + RestConstants.REPRESENTATION_FULL);
			return description;
		}
		if (rep instanceof FullRepresentation) {
			DelegatingResourceDescription description = new DelegatingResourceDescription();
			description.addProperty("uuid");
			description.addProperty("display");
			description.addProperty("name");
			description.addProperty("description");
			description.addProperty("retired");
			description.addProperty("retireReason");
			description.addProperty("entries");
			description.addProperty("latestBuild", Representation.DEFAULT);
			description.addProperty("auditInfo");
			description.addSelfLink();
			return description;
		}
		return null;
	}
	
	@Override
	public DelegatingResourceDescription getCreatableProperties() {
		DelegatingResourceDescription description = new DelegatingResourceDescription();
		description.addRequiredProperty("name");
		description.addProperty("description");
		description.addRequiredProperty("entries");
		return description;
	}
	
	@PropertyGetter("entries")
	public static List<SimpleObject> getEntries(ExportPackage exportPackage) {
		List<SimpleObject> entries = new ArrayList<>();
		for (ExportPackageEntry entry : exportPackage.getEntries()) {
			entries.add(
			    new SimpleObject().add("domain", entry.getDomain()).add("itemUuids", new ArrayList<>(entry.getItemUuids())));
		}
		return entries;
	}
	
	/**
	 * Replaces the entries wholesale; PUT semantics. An explicit empty list widens the package to every
	 * registered domain, which is why {@code entries} is required on create rather than defaulted, and
	 * why a malformed entry (unknown key, non-list {@code itemUuids}) is rejected instead of being read
	 * as "no uuids": that would silently widen the export to the whole domain. The domain's validity
	 * and the uuid values are checked by {@code ExportPackageValidator}.
	 */
	@PropertySetter("entries")
	public static void setEntries(ExportPackage exportPackage, List<Object> entries) {
		exportPackage.getEntries().clear();
		for (int i = 0; i < entries.size(); i++) {
			String path = "entries[" + i + "]";
			if (!(entries.get(i) instanceof Map)) {
				throw new ConversionException(path + " must be an object {domain, itemUuids}");
			}
			Map<?, ?> entryMap = (Map<?, ?>) entries.get(i);
			Set<Object> unknownKeys = new LinkedHashSet<>(entryMap.keySet());
			unknownKeys.removeAll(ENTRY_KEYS);
			if (!unknownKeys.isEmpty()) {
				throw new ConversionException(path + " has unknown properties " + unknownKeys + "; allowed: " + ENTRY_KEYS);
			}
			Object domain = entryMap.get("domain");
			if (domain == null) {
				throw new ConversionException(path + ".domain is required");
			}
			Object itemUuids = entryMap.get("itemUuids");
			if (itemUuids != null && !(itemUuids instanceof Collection)) {
				throw new ConversionException(path + ".itemUuids must be a list of uuids");
			}
			ExportPackageEntry entry = new ExportPackageEntry();
			entry.setDomain(domain.toString().trim().toUpperCase(Locale.ROOT));
			if (itemUuids != null) {
				LinkedHashSet<String> deduped = new LinkedHashSet<>();
				for (Object itemUuid : (Collection<?>) itemUuids) {
					deduped.add(itemUuid == null ? null : itemUuid.toString());
				}
				entry.getItemUuids().addAll(deduped);
			}
			entry.setExportPackage(exportPackage);
			exportPackage.getEntries().add(entry);
		}
	}
	
	/**
	 * Rendered through {@code ExportBuildResource}. Guarded because the unsaved delegate of a create
	 * has no id yet.
	 */
	@PropertyGetter("latestBuild")
	public static ExportBuild getLatestBuild(ExportPackage exportPackage) {
		return exportPackage.getId() == null ? null : service().getLatestBuild(exportPackage);
	}
	
	private static MetadataExportService service() {
		return Context.getService(MetadataExportService.class);
	}
}
