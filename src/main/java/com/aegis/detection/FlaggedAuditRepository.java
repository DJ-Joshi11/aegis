package com.aegis.detection;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FlaggedAuditRepository extends JpaRepository<FlaggedAudit, String> {

    List<FlaggedAudit> findAllByOrderByReviewedAtDesc();
}
