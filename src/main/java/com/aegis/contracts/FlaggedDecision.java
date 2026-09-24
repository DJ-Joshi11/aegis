package com.aegis.contracts;

/**
 * A DecisionEvent that has been checked by the detection package: whether
 * it looks anomalous, why, and what the compliance status is for its
 * jurisdiction.
 *
 * OWNERSHIP: shared contract. Do not change without agreement from all
 * three teammates — payout depends on this shape exactly as it stands.
 */
public record FlaggedDecision(
        DecisionEvent decision,
        boolean flagged,
        String flagReason,
        String complianceStatus
) {}
