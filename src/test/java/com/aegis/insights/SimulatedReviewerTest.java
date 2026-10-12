package com.aegis.insights;

import com.aegis.contracts.DecisionEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulatedReviewerTest {

    private static DecisionEvent d(String agent, String type, String outcome, String jur, String reasoning, Double conf) {
        return new DecisionEvent("x", agent, type, outcome, Instant.now(), jur, reasoning, "PERSONAL_AUTO", 5000.0, conf);
    }

    @Test
    void driftedModelDenialsAreReversedFarMoreOftenThanOrdinaryOnes() {
        double drifted = SimulatedReviewer.overturnProbability(d("qsr-claims-agent-07", "CLAIMS", "DENIED", "US-CA",
                "Severity model v2 score 0.74 exceeds auto-deny cutoff 0.70; claim of $5,000 denied.", 0.92));
        double ordinary = SimulatedReviewer.overturnProbability(d("qsr-claims-agent-02", "CLAIMS", "DENIED", "US-CA",
                "Loss occurred 11 days after policy lapse; no coverage in force at time of loss; claim of $5,000 denied.", 0.85));
        assertTrue(drifted > 0.5);
        assertTrue(ordinary < 0.15);
    }

    @Test
    void lowConfidenceRaisesTheChanceOfReversal() {
        String r = "Loss occurred 11 days after policy lapse; no coverage in force at time of loss; claim denied.";
        double sure = SimulatedReviewer.overturnProbability(d("a", "CLAIMS", "DENIED", "US-CA", r, 0.9));
        double unsure = SimulatedReviewer.overturnProbability(d("a", "CLAIMS", "DENIED", "US-CA", r, 0.5));
        assertEquals(0.15, unsure - sure, 1e-9);
    }

    @Test
    void roughlyOneInFiveDecisionsIsLeftForAHumanAndTheChoiceIsStable() {
        int left = 0;
        for (int i = 0; i < 5000; i++) {
            if (SimulatedReviewer.leftForHumans("decision-" + i)) {
                left++;
            }
        }
        assertTrue(left > 850 && left < 1150, "left=" + left);
        assertEquals(SimulatedReviewer.leftForHumans("abc"), SimulatedReviewer.leftForHumans("abc"));
    }
}
