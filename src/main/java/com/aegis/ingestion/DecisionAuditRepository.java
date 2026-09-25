package com.aegis.ingestion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Audit-log persistence for ingested decisions. H2-backed via
 * {@code spring-boot-starter-data-jpa}, table auto-created by Hibernate
 * (see {@code application.properties}: {@code spring.jpa.hibernate.ddl-auto=update}).
 */
public interface DecisionAuditRepository extends JpaRepository<DecisionAudit, String> {

    List<DecisionAudit> findAllByOrderByTimestampDesc();
}
