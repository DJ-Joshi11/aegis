package com.aegis.detection;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity backing the flagged-decisions audit table (H2).
 *
 * Only decisions detection actually flagged are stored here — same pattern
 * as {@code com.aegis.ingestion.DecisionAudit} and
 * {@code com.aegis.payout.PayoutAudit}: a persistence-layer concern,
 * separate from the shared {@code FlaggedDecision} contract record.
 */
@Entity
@Table(name = "flagged_audit")
public class FlaggedAudit {

    @Id
    @Column(nullable = false, updatable = false, length = 100)
    private String decisionId;

    @Column(nullable = false, length = 150)
    private String agentId;

    @Column(nullable = false, length = 100)
    private String decisionType;

    @Column(nullable = false, length = 100)
    private String outcome;

    @Column(nullable = false, length = 50)
    private String jurisdiction;

    @Column(nullable = false, length = 500)
    private String flagReason;

    @Column(nullable = false, length = 200)
    private String complianceStatus;

    @Column(nullable = false)
    private Instant reviewedAt;

    protected FlaggedAudit() {
        // JPA
    }

    public FlaggedAudit(String decisionId, String agentId, String decisionType, String outcome,
                         String jurisdiction, String flagReason, String complianceStatus, Instant reviewedAt) {
        this.decisionId = decisionId;
        this.agentId = agentId;
        this.decisionType = decisionType;
        this.outcome = outcome;
        this.jurisdiction = jurisdiction;
        this.flagReason = flagReason;
        this.complianceStatus = complianceStatus;
        this.reviewedAt = reviewedAt;
    }

    public String getDecisionId() {
        return decisionId;
    }

    public String getAgentId() {
        return agentId;
    }

    public String getDecisionType() {
        return decisionType;
    }

    public String getOutcome() {
        return outcome;
    }

    public String getJurisdiction() {
        return jurisdiction;
    }

    public String getFlagReason() {
        return flagReason;
    }

    public String getComplianceStatus() {
        return complianceStatus;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }
}
