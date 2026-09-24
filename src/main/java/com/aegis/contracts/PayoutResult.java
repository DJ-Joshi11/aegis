package com.aegis.contracts;

/**
 * The outcome of the payout package acting on a FlaggedDecision: whether a
 * payout was triggered, how much, and whether it was due to an SLA breach.
 *
 * OWNERSHIP: shared contract. Do not change without agreement from all
 * three teammates.
 */
public record PayoutResult(
        String decisionId,
        boolean payoutTriggered,
        double amount,
        boolean slaBreached
) {}
