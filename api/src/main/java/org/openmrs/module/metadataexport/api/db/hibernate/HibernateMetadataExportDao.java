/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.metadataexport.api.db.hibernate;

import lombok.RequiredArgsConstructor;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.openmrs.module.metadataexport.api.db.MetadataExportDao;
import org.openmrs.module.metadataexport.api.model.ExportBuild;
import org.openmrs.module.metadataexport.api.model.ExportPackage;
import org.openmrs.module.metadataexport.api.model.ExportStatus;

import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import java.util.Arrays;
import java.util.List;

@RequiredArgsConstructor
public class HibernateMetadataExportDao implements MetadataExportDao {
	
	private final SessionFactory sessionFactory;
	
	@Override
	public ExportPackage savePackage(ExportPackage exportPackage) {
		sessionFactory.getCurrentSession().saveOrUpdate(exportPackage);
		return exportPackage;
	}
	
	@Override
	public ExportPackage getPackageByUuid(String uuid) {
		TypedQuery<ExportPackage> query = sessionFactory.getCurrentSession()
		        .createQuery("from ExportPackage pkg where  pkg.uuid = :uuid", ExportPackage.class);
		query.setParameter("uuid", uuid);
		return query.getResultStream().findFirst().orElse(null);
	}
	
	@Override
	public ExportPackage getPackageByName(String name) {
		TypedQuery<ExportPackage> query = sessionFactory.getCurrentSession()
		        .createQuery("from ExportPackage pkg where pkg.name = :name and pkg.retired = false", ExportPackage.class);
		query.setParameter("name", name);
		return query.getResultStream().findFirst().orElse(null);
	}
	
	@Override
	public List<ExportPackage> getPackages(boolean includeRetired, int startIndex, int limit) {
		return window(packagesQuery(includeRetired), startIndex, limit).getResultList();
	}
	
	@Override
	public long getCountOfPackages(boolean includeRetired) {
		Session session = sessionFactory.getCurrentSession();
		CriteriaBuilder cb = session.getCriteriaBuilder();
		CriteriaQuery<Long> cq = cb.createQuery(Long.class);
		Root<ExportPackage> root = cq.from(ExportPackage.class);
		cq.select(cb.count(root));
		if (!includeRetired) {
			cq.where(cb.isFalse(root.get("retired")));
		}
		return session.createQuery(cq).getSingleResult();
	}
	
	/**
	 * Ordered by name then id: the name is unique among unretired packages and the id breaks ties with
	 * retired predecessors of the same name, so consecutive pages never overlap or skip rows.
	 */
	private TypedQuery<ExportPackage> packagesQuery(boolean includeRetired) {
		Session session = sessionFactory.getCurrentSession();
		CriteriaBuilder cb = session.getCriteriaBuilder();
		CriteriaQuery<ExportPackage> cq = cb.createQuery(ExportPackage.class);
		Root<ExportPackage> root = cq.from(ExportPackage.class);
		if (!includeRetired) {
			cq.where(cb.isFalse(root.get("retired")));
		}
		cq.orderBy(cb.asc(root.get("name")), cb.asc(root.get("packageId")));
		return session.createQuery(cq);
	}
	
	@Override
	public ExportBuild saveBuild(ExportBuild exportBuild) {
		sessionFactory.getCurrentSession().saveOrUpdate(exportBuild);
		return exportBuild;
	}
	
	@Override
	public ExportBuild getBuildByUuid(String uuid) {
		TypedQuery<ExportBuild> query = sessionFactory.getCurrentSession()
		        .createQuery("from ExportBuild build where build.uuid = :uuid", ExportBuild.class);
		query.setParameter("uuid", uuid);
		return query.getResultStream().findFirst().orElse(null);
	}
	
	@Override
	public List<ExportBuild> getBuilds(ExportPackage exportPackage) {
		return buildsQuery(exportPackage).getResultList();
	}
	
	@Override
	public List<ExportBuild> getBuilds(ExportPackage exportPackage, int startIndex, int limit) {
		return window(buildsQuery(exportPackage), startIndex, limit).getResultList();
	}
	
	@Override
	public long getCountOfBuilds(ExportPackage exportPackage) {
		TypedQuery<Long> query = sessionFactory.getCurrentSession().createQuery(
		    "select count(build) from ExportBuild build where build.exportPackage = :exportPackage", Long.class);
		query.setParameter("exportPackage", exportPackage);
		return query.getSingleResult();
	}
	
	@Override
	public ExportBuild getLatestBuild(ExportPackage exportPackage) {
		return window(buildsQuery(exportPackage), 0, 1).getResultStream().findFirst().orElse(null);
	}
	
	private TypedQuery<ExportBuild> buildsQuery(ExportPackage exportPackage) {
		TypedQuery<ExportBuild> query = sessionFactory.getCurrentSession().createQuery(
		    "from ExportBuild build where build.exportPackage = :exportPackage order by build.version desc",
		    ExportBuild.class);
		query.setParameter("exportPackage", exportPackage);
		return query;
	}
	
	@Override
	public List<ExportBuild> getActiveBuilds() {
		TypedQuery<ExportBuild> query = sessionFactory.getCurrentSession()
		        .createQuery("from ExportBuild build where build.exportStatus in (:statuses)", ExportBuild.class);
		query.setParameter("statuses", Arrays.asList(ExportStatus.QUEUED, ExportStatus.RUNNING));
		return query.getResultList();
	}
	
	/**
	 * Hibernate rejects a negative offset itself, but treats a zero limit as "no limit", so guard both.
	 */
	private static <T> TypedQuery<T> window(TypedQuery<T> query, int startIndex, int limit) {
		if (startIndex < 0) {
			throw new IllegalArgumentException("startIndex must be 0 or greater, got " + startIndex);
		}
		if (limit < 1) {
			throw new IllegalArgumentException("limit must be 1 or greater, got " + limit);
		}
		query.setFirstResult(startIndex);
		query.setMaxResults(limit);
		return query;
	}
}
