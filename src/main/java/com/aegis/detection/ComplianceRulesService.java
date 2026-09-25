package com.aegis.detection;

import org.springframework.stereotype.Service;

/**
 * Maps a decision's jurisdiction to the explainability/disclosure standard
 * that jurisdiction requires — the "Comply" mechanic from the project's
 * USP: AEGIS is regulation-aware by design, not bolted on later.
 *
 * Deliberately a simple lookup table, not a legal-compliance engine — it
 * demonstrates that jurisdiction rules differ and are checked automatically,
 * which is the point for this build.
 */
@Service
public class ComplianceRulesService {

    /** What a flagged decision must satisfy, by jurisdiction prefix. */
    public String requiredStandardFor(String jurisdiction) {
        if (jurisdiction == null) {
            return "General AI decision disclosure";
        }
        if (jurisdiction.startsWith("EU-")) {
            return "EU AI Act Art.14 — human oversight & explainability";
        }
        if (jurisdiction.startsWith("IN-")) {
            return "IRDAI algorithmic decision disclosure";
        }
        if (jurisdiction.startsWith("US-")) {
            return "State insurance-AI disclosure rule";
        }
        if (jurisdiction.startsWith("UK-")) {
            return "UK FCA AI governance principles";
        }
        return "General AI decision disclosure";
    }

    /** Status text for a decision that was reviewed and NOT flagged. */
    public String notApplicableStatus() {
        return "Not applicable — decision cleared, no compliance review required";
    }
}
