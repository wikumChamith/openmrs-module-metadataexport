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

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.openmrs.api.APIException;
import org.openmrs.api.context.Context;
import org.openmrs.module.metadataexport.api.ActiveBuildException;
import org.openmrs.module.metadataexport.api.ExportJobRunner;
import org.openmrs.module.metadataexport.api.MetadataExportService;
import org.openmrs.module.metadataexport.api.RetiredPackageException;
import org.openmrs.module.metadataexport.api.model.ExportBuild;
import org.openmrs.module.metadataexport.api.model.ExportPackage;
import org.openmrs.module.metadataexport.api.model.ExportStatus;
import org.openmrs.module.metadataexport.web.controller.MetadataExportRestConstants;
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
import org.openmrs.module.webservices.rest.web.resource.impl.DataDelegatingCrudResource;
import org.openmrs.module.webservices.rest.web.resource.impl.DelegatingResourceDescription;
import org.openmrs.module.webservices.rest.web.response.ConversionException;
import org.openmrs.module.webservices.rest.web.response.ObjectNotFoundException;
import org.openmrs.module.webservices.rest.web.response.ResourceDoesNotSupportOperationException;
import org.openmrs.module.webservices.rest.web.response.ResponseException;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * {@code /ws/rest/v1/metadataexport/builds}. Builds are an immutable record of one export run: the
 * only write is {@code POST {"package": "<uuid>"}} on the collection, which triggers a build and
 * answers 201 with the QUEUED build (409 if the package is retired or already has an active build).
 * Update, void and purge are refused. The zip itself is served outside the resource framework by
 * {@code ExportBuildController} at the {@code downloadUrl} property. List with
 * {@code ?package=<uuid>} (404 for an unknown package), paged newest-version-first; there is no
 * unfiltered list.
 */
@Slf4j
@Resource(name = MetadataExportRestConstants.NAMESPACE
        + "/builds", supportedClass = ExportBuild.class, supportedOpenmrsVersions = {
                MetadataExportRestConstants.SUPPORTED_OPENMRS_VERSIONS })
public class ExportBuildResource extends DataDelegatingCrudResource<ExportBuild> {
	
	private static final ObjectMapper MANIFEST_MAPPER = new ObjectMapper();
	
	@Override
	public ExportBuild getByUniqueId(String uuid) {
		return service().getBuildByUuid(uuid);
	}
	
	@Override
	public ExportBuild newDelegate() {
		return new ExportBuild();
	}
	
	/**
	 * Reached only from {@code create} (update is refused below): the delegate is a throwaway carrying
	 * the target package, and the persisted build is the one the job runner queues.
	 */
	@Override
	public ExportBuild save(ExportBuild build) {
		ExportPackage exportPackage = build.getExportPackage();
		if (exportPackage == null) {
			// the framework rejects a missing "package" before save(); the setter rejects an unknown one
			throw new IllegalStateException("save() reached without a package");
		}
		try {
			return jobRunner().trigger(exportPackage.getUuid());
		}
		catch (RetiredPackageException | ActiveBuildException e) {
			throw new ConflictException(e.getMessage(), e);
		}
	}
	
	/**
	 * Without this the framework would route {@code POST /builds/{uuid}} into {@link #save} as an
	 * update.
	 */
	@Override
	public DelegatingResourceDescription getUpdatableProperties() throws ResourceDoesNotSupportOperationException {
		throw new ResourceDoesNotSupportOperationException(
		        "Builds are immutable; POST {\"package\": \"<uuid>\"} to the collection to trigger a new one");
	}
	
	@Override
	protected void delete(ExportBuild build, String reason, RequestContext context) throws ResponseException {
		throw new ResourceDoesNotSupportOperationException("Builds are an immutable history and cannot be voided");
	}
	
	@Override
	public void purge(ExportBuild build, RequestContext context) throws ResponseException {
		throw new ResourceDoesNotSupportOperationException("Builds are an immutable history and cannot be purged");
	}
	
	@Override
	protected PageableResult doGetAll(RequestContext context) throws ResponseException {
		throw new ResourceDoesNotSupportOperationException("List builds of one package with ?package=<uuid>");
	}
	
	@Override
	protected PageableResult doSearch(RequestContext context) {
		String packageUuid = context.getParameter("package");
		if (packageUuid == null) {
			throw new ResourceDoesNotSupportOperationException("List builds of one package with ?package=<uuid>");
		}
		ExportPackage exportPackage = service().getPackageByUuid(packageUuid);
		if (exportPackage == null) {
			throw new ObjectNotFoundException("No export package with uuid " + packageUuid);
		}
		int startIndex = RestPaging.startIndex(context);
		List<ExportBuild> page = service().getBuilds(exportPackage, startIndex, context.getLimit());
		long total = service().getCountOfBuilds(exportPackage);
		return new AlreadyPaged<>(context, page, RestPaging.hasMore(startIndex, page.size(), total), total);
	}
	
	@Override
	public DelegatingResourceDescription getRepresentationDescription(Representation rep) {
		if (rep instanceof DefaultRepresentation) {
			DelegatingResourceDescription description = describe();
			description.addSelfLink();
			description.addLink("full", ".?v=" + RestConstants.REPRESENTATION_FULL);
			return description;
		}
		if (rep instanceof FullRepresentation) {
			DelegatingResourceDescription description = describe();
			description.addProperty("manifest");
			description.addProperty("auditInfo");
			description.addSelfLink();
			return description;
		}
		return null;
	}
	
	private static DelegatingResourceDescription describe() {
		DelegatingResourceDescription description = new DelegatingResourceDescription();
		description.addProperty("uuid");
		description.addProperty("display");
		description.addProperty("package", Representation.REF);
		description.addProperty("version");
		description.addProperty("status");
		description.addProperty("dateCreated");
		description.addProperty("dateStarted");
		description.addProperty("dateCompleted");
		description.addProperty("errorMessage");
		description.addProperty("downloadUrl");
		return description;
	}
	
	@Override
	public DelegatingResourceDescription getCreatableProperties() {
		DelegatingResourceDescription description = new DelegatingResourceDescription();
		description.addRequiredProperty("package");
		return description;
	}
	
	@PropertyGetter("display")
	public static String getDisplayString(ExportBuild build) {
		String packageName = build.getExportPackage() == null ? "?" : build.getExportPackage().getName();
		return packageName + " v" + build.getVersion() + " (" + build.getExportStatus() + ")";
	}
	
	@PropertyGetter("package")
	public static ExportPackage getPackage(ExportBuild build) {
		return build.getExportPackage();
	}
	
	/**
	 * Resolves the uuid here rather than letting the framework convert it, so an unknown uuid is
	 * reported with the value the client sent instead of arriving as a silent null.
	 */
	@PropertySetter("package")
	public static void setPackage(ExportBuild build, Object value) {
		Object uuid = value instanceof Map ? ((Map<?, ?>) value).get("uuid") : value;
		ExportPackage exportPackage = uuid == null ? null : service().getPackageByUuid(uuid.toString());
		if (exportPackage == null) {
			throw new ConversionException("No export package with uuid '" + uuid + "'");
		}
		build.setExportPackage(exportPackage);
	}
	
	@PropertyGetter("status")
	public static String getStatus(ExportBuild build) {
		return build.getExportStatus() == null ? null : build.getExportStatus().name();
	}
	
	/**
	 * Absolute, since the zip is served outside the resource framework; null until the build is
	 * COMPLETED.
	 */
	@PropertyGetter("downloadUrl")
	public String getDownloadUrl(ExportBuild build) {
		if (build.getExportStatus() != ExportStatus.COMPLETED) {
			return null;
		}
		return getUri(build) + "/download";
	}
	
	/**
	 * The manifest is written by this module, so unparseable JSON is a corrupt record, not a client
	 * error.
	 */
	@PropertyGetter("manifest")
	public static Map<?, ?> getManifest(ExportBuild build) {
		if (build.getManifestJson() == null) {
			return null;
		}
		try {
			return MANIFEST_MAPPER.readValue(build.getManifestJson(), Map.class);
		}
		catch (IOException e) {
			log.error("Metadata Export: the stored manifest of build {} is not valid JSON", build.getUuid(), e);
			throw new APIException("The stored manifest of build " + build.getUuid()
			        + " could not be parsed; the build record is corrupt. See the server log.", e);
		}
	}
	
	private static MetadataExportService service() {
		return Context.getService(MetadataExportService.class);
	}
	
	/**
	 * Resources are instantiated reflectively by the REST module, not as Spring beans, hence no
	 * injection.
	 */
	private static ExportJobRunner jobRunner() {
		List<ExportJobRunner> runners = Context.getRegisteredComponents(ExportJobRunner.class);
		if (runners.isEmpty()) {
			throw new IllegalStateException(
			        "ExportJobRunner is not registered in the Spring context; is the module started?");
		}
		return runners.get(0);
	}
}
