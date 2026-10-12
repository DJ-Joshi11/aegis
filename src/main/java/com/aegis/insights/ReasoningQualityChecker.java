package com.aegis.insights;

import com.aegis.contracts.DecisionEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Rule-based checks on the reasoning trail an agent logged with its
 * decision. A regulator asking "why was this claim denied?" needs a
 * specific, evidence-based answer; these checks find decisions where the
 * logged reasoning can't give one. Deterministic and explainable — no model.
 */
final class ReasoningQualityChecker {

    static final String THIN = "THIN_REASONING";
    static final String NO_EVIDENCE = "NO_QUANTITATIVE_BASIS";
    static final String CONTRADICTION = "OUTCOME_CONTRADICTS_REASONING";
    static final String LOW_CONFIDENCE_AUTO = "LOW_CONFIDENCE_AUTO_DECISION";

    /** Below this many characters a reasoning trail can't explain a decision. */
    static final int MIN_LENGTH = 40;

    /** Outcomes that hurt the customer and therefore need a cited basis. */
    static final Set<String> ADVERSE = Set.of("DENIED", "PARTIAL_APPROVAL", "FLAGGED");

    /** Outcomes where the agent already handed the call to a person. */
    private static final Set<String> HANDED_OFF = Set.of("REFERRED_TO_HUMAN", "FLAGGED");

    private final double lowConfidenceThreshold;

    ReasoningQualityChecker(double lowConfidenceThreshold) {
        this.lowConfidenceThreshold = lowConfidenceThreshold;
    }

    List<String> check(DecisionEvent d) {
        List<String> issues = new ArrayList<>();
        String text = d.reasoningTrail() == null ? "" : d.reasoningTrail().trim();
        String lower = text.toLowerCase(Locale.ROOT);
        String outcome = d.outcome() == null ? "" : d.outcome();

        if (text.length() < MIN_LENGTH) {
            issues.add(THIN);
        }
        if (ADVERSE.contains(outcome) && text.chars().noneMatch(Character::isDigit)) {
            issues.add(NO_EVIDENCE);
        }
        if (contradicts(outcome, lower)) {
            issues.add(CONTRADICTION);
        }
        if (d.confidence() != null && d.confidence() < lowConfidenceThreshold && !HANDED_OFF.contains(outcome)) {
            issues.add(LOW_CONFIDENCE_AUTO);
        }
        return issues;
    }

    private static boolean contradicts(String outcome, String lower) {
        return switch (outcome) {
            case "DENIED" -> lower.contains("approved in full") || lower.contains("auto-approved");
            case "APPROVED" -> lower.contains("denied") || lower.contains("declined");
            case "FLAGGED" -> lower.contains("no fraud indicators") || lower.contains("cleared");
            case "CLEARED" -> lower.contains("flagged for siu");
            default -> false;
        };
    }
}
