package com.aegis.contracts;

import java.time.Instant;

/**
 * A single AI-agent decision, as logged by the ingestion package.
 * Shaped to match what a Guidewire Qusar-style underwriting/claims agent
 * would emit (see the main README's "How we're using Qusar" section).
 *
 * OWNERSHIP: shared contract. Do not change without agreement from all
 * three teammates — detection and payout both depend on this shape exactly
 * as it stands. Add fields in their own small PR, never inside a feature branch.
 */
public record DecisionEvent(
        String decisionId,
        String agentId,
        String decisionType,
        String outcome,
        Instant timestamp,
        String jurisdiction,
        String reasoningTrail
) {}
