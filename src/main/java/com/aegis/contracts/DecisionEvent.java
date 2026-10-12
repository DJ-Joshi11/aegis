package com.aegis.contracts;

import java.time.Instant;

/**
 * A single AI-agent decision, as logged by the ingestion package.
 * Shaped to match what a Guidewire Qusar-style underwriting/claims agent
 * would emit (see the main README's "How we're using Qusar" section).
 *
 * OWNERSHIP: shared contract. Do not change without agreement from all
 * three teammates — detection and evidence both depend on this shape exactly
 * as it stands. Add fields in their own small PR, never inside a feature branch.
 *
 * The last three fields are OPTIONAL (nullable) and were added for the
 * insights engine; a feed that doesn't send them is still accepted:
 * <ul>
 *   <li>{@code lineOfBusiness} — Guidewire product line the decision belongs to
 *   (e.g. PERSONAL_AUTO, HOMEOWNERS, COMMERCIAL_PROPERTY, WORKERS_COMP).</li>
 *   <li>{@code amount} — monetary value the decision is about: the claimed
 *   amount for CLAIMS / FRAUD_FLAG, the sum insured for UNDERWRITING. Never a premium.</li>
 *   <li>{@code confidence} — the agent's own self-reported confidence, 0..1.</li>
 * </ul>
 */
public record DecisionEvent(
        String decisionId,
        String agentId,
        String decisionType,
        String outcome,
        Instant timestamp,
        String jurisdiction,
        String reasoningTrail,
        String lineOfBusiness,
        Double amount,
        Double confidence
) {}
