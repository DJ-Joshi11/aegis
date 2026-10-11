package com.aegis.contracts;

/**
 * The outcome of the evidence package mapping a {@link FlaggedDecision} (or
 * an unreviewed decision past the review window) to an audit evidence
 * record: whether evidence was recorded, which broad insurance exposure
 * category the decision maps to, and whether a control gap was identified.
 *
 * AEGIS does not certify compliance, make legal coverage determinations, or
 * set premiums from this record — it is evidence for a human compliance or
 * underwriting reviewer to act on, not an automated decision of its own.
 *
 * OWNERSHIP: shared contract. Do not change without agreement from all
 * three teammates.
 */
public record ExposureEvidenceRecord(
        String decisionId,
        boolean evidenceRecorded,
        String exposureCategory,
        boolean controlGapIdentified
) {}
