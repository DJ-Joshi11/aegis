package com.aegis.evidence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity backing the audit evidence table (H2).
 *
 * Mirrors the shape of the shared
 * {@code com.aegis.contracts.ExposureEvidenceRecord} record plus the
 * bookkeeping {@link EvidenceMappingService} needs to show a real audit
 * trail (who, why, when, which exposure category) — same pattern as
 * {@code com.aegis.ingestion.DecisionAudit}: the contract record stays
 * untouched, this is a persistence-layer concern only.
 *
 * Deliberately carries no monetary amount and sets no premium or coverage
 * term — this is an evidence row for a human reviewer, not a payout or a
 * pricing decision.
 */
@Entity
@Table(name = "evidence_record")
public class EvidenceRecord {

    @Id
    @Column(nullable = false, updatable = false, length = 100)
    private String decisionId;

    @Column(nullable = false, length = 150)
    private String agentId;

    @Column(nullable = false, length = 100)
    private String exposureCategory;

    @Column(nullable = false)
    private boolean controlGapIdentified;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(nullable = false)
    private Instant recordedAt;

    protected EvidenceRecord() {
        // JPA
    }

    public EvidenceRecord(String decisionId, String agentId, String exposureCategory, boolean controlGapIdentified,
                           String reason, Instant recordedAt) {
        this.decisionId = decisionId;
        this.agentId = agentId;
        this.exposureCategory = exposureCategory;
        this.controlGapIdentified = controlGapIdentified;
        this.reason = reason;
        this.recordedAt = recordedAt;
    }

    public String getDecisionId() {
        return decisionId;
    }

    public String getAgentId() {
        return agentId;
    }

    public String getExposureCategory() {
        return exposureCategory;
    }

    public boolean isControlGapIdentified() {
        return controlGapIdentified;
    }

    public String getReason() {
        return reason;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}
