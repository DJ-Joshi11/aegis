package com.aegis.ingestion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity backing the ingestion audit-log table (H2).
 *
 * This is a persistence-layer concern only — it mirrors the shared
 * {@code com.aegis.contracts.DecisionEvent} record but is intentionally a
 * separate type, since the contract record must stay untouched and JPA
 * entities need to be mutable, ID-bearing classes. {@link DecisionIngestionService}
 * maps between the two.
 *
 * {@code decisionId} is used as the primary key: it is the natural key a
 * Qusar-style agent decision log would already carry, and using it directly
 * lets us reject duplicate ingestion of the same decision.
 */
@Entity
@Table(name = "decision_audit")
public class DecisionAudit {

    @Id
    @Column(nullable = false, updatable = false, length = 100)
    private String decisionId;

    @Column(nullable = false, length = 150)
    private String agentId;

    @Column(nullable = false, length = 100)
    private String decisionType;

    @Column(nullable = false, length = 100)
    private String outcome;

    @Column(nullable = false)
    private Instant timestamp;

    @Column(nullable = false, length = 50)
    private String jurisdiction;

    @Column(nullable = false, length = 4000)
    private String reasoningTrail;

    /** When AEGIS itself received/ingested this decision — distinct from the agent's own decision timestamp. */
    @Column(nullable = false)
    private Instant ingestedAt;

    protected DecisionAudit() {
        // JPA
    }

    public DecisionAudit(String decisionId, String agentId, String decisionType, String outcome,
                          Instant timestamp, String jurisdiction, String reasoningTrail, Instant ingestedAt) {
        this.decisionId = decisionId;
        this.agentId = agentId;
        this.decisionType = decisionType;
        this.outcome = outcome;
        this.timestamp = timestamp;
        this.jurisdiction = jurisdiction;
        this.reasoningTrail = reasoningTrail;
        this.ingestedAt = ingestedAt;
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

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getJurisdiction() {
        return jurisdiction;
    }

    public String getReasoningTrail() {
        return reasoningTrail;
    }

    public Instant getIngestedAt() {
        return ingestedAt;
    }
}
