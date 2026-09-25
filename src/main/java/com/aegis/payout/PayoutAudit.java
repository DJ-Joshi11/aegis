package com.aegis.payout;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity backing the payout audit-log table (H2).
 *
 * Mirrors the shape of the shared {@code com.aegis.contracts.PayoutResult}
 * record plus the bookkeeping PayoutEngineService needs to show a real
 * audit trail (who, why, when) — same pattern as
 * {@code com.aegis.ingestion.DecisionAudit}: the contract record stays
 * untouched, this is a persistence-layer concern only.
 */
@Entity
@Table(name = "payout_audit")
public class PayoutAudit {

    @Id
    @Column(nullable = false, updatable = false, length = 100)
    private String decisionId;

    @Column(nullable = false, length = 150)
    private String agentId;

    @Column(nullable = false)
    private double amount;

    @Column(nullable = false)
    private boolean slaBreached;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(nullable = false)
    private Instant triggeredAt;

    protected PayoutAudit() {
        // JPA
    }

    public PayoutAudit(String decisionId, String agentId, double amount, boolean slaBreached,
                        String reason, Instant triggeredAt) {
        this.decisionId = decisionId;
        this.agentId = agentId;
        this.amount = amount;
        this.slaBreached = slaBreached;
        this.reason = reason;
        this.triggeredAt = triggeredAt;
    }

    public String getDecisionId() {
        return decisionId;
    }

    public String getAgentId() {
        return agentId;
    }

    public double getAmount() {
        return amount;
    }

    public boolean isSlaBreached() {
        return slaBreached;
    }

    public String getReason() {
        return reason;
    }

    public Instant getTriggeredAt() {
        return triggeredAt;
    }
}
