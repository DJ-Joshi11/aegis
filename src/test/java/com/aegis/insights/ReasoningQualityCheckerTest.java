package com.aegis.insights;

import com.aegis.contracts.DecisionEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReasoningQualityCheckerTest {

    private final ReasoningQualityChecker checker = new ReasoningQualityChecker(0.6);

    private static DecisionEvent d(String type, String outcome, String reasoning, Double confidence) {
        return new DecisionEvent("d", "agent", type, outcome, Instant.now(), "US-CA", reasoning, "HOMEOWNERS", 1000.0, confidence);
    }

    @Test
    void wellEvidencedDecisionHasNoIssues() {
        List<String> issues = checker.check(d("CLAIMS", "DENIED",
                "Loss occurred 11 days after policy lapse; no coverage in force at time of loss; claim denied.", 0.9));
        assertTrue(issues.isEmpty(), issues.toString());
    }

    @Test
    void oneLineDenialIsThinAndHasNoQuantitativeBasis() {
        List<String> issues = checker.check(d("UNDERWRITING", "DENIED", "Auto-decision per model score.", 0.8));
        assertTrue(issues.contains(ReasoningQualityChecker.THIN));
        assertTrue(issues.contains(ReasoningQualityChecker.NO_EVIDENCE));
    }

    @Test
    void approvalDoesNotNeedFiguresButDenialDoes() {
        String text = "Applicant history consistent with appetite guidelines and underwriting rules; approved.";
        assertTrue(checker.check(d("UNDERWRITING", "APPROVED", text, 0.9)).isEmpty());
        assertTrue(checker.check(d("UNDERWRITING", "DENIED",
                "Applicant history inconsistent with appetite guidelines and underwriting rules.", 0.9))
                .contains(ReasoningQualityChecker.NO_EVIDENCE));
    }

    @Test
    void outcomeThatContradictsItsReasoningIsCaught() {
        List<String> issues = checker.check(d("CLAIMS", "DENIED",
                "Damage estimate $4,200 consistent with submitted photos; approved in full.", 0.9));
        assertTrue(issues.contains(ReasoningQualityChecker.CONTRADICTION));
    }

    @Test
    void lowConfidenceOnlyMattersWhenTheAgentDecidedAlone() {
        String text = "Claimant history and loss circumstances consistent with baseline over 24 months; cleared.";
        assertTrue(checker.check(d("FRAUD_FLAG", "CLEARED", text, 0.5)).contains(ReasoningQualityChecker.LOW_CONFIDENCE_AUTO));
        assertEquals(List.of(), checker.check(d("UNDERWRITING", "REFERRED_TO_HUMAN",
                "Confidence 0.50 below auto-decision threshold, referred to human underwriter.", 0.5)));
    }
}
