package com.aegis.insights;

import com.aegis.contracts.DecisionEvent;
import com.aegis.insights.InsightsReport.AgentInsight;
import com.aegis.insights.InsightsReport.LineInsight;
import com.aegis.insights.InsightsReport.SegmentDisparity;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The engine is a pure function, so every test builds a small, fully
 * deterministic decision history with a known pattern and checks the
 * engine finds that pattern — and doesn't find patterns that aren't there.
 */
class InsightsEngineTest {

    private static final Instant NOW = Instant.parse("2026-10-12T12:00:00Z");
    private static final String GOOD_REASON = "Loss occurred 11 days after policy lapse; no coverage in force; claim of $4,000 denied.";
    private static final String OK_REASON = "Damage estimate $4,200 consistent with submitted photos and adjuster notes; approved in full.";

    private final InsightsEngine engine = new InsightsEngine(InsightsEngine.Config.defaults());

    private final List<DecisionEvent> decisions = new ArrayList<>();
    private final Map<String, ReviewVerdict> verdicts = new HashMap<>();
    private int seq = 0;

    /** Adds n decisions for one agent; every decision whose index satisfies the pattern is adverse. */
    private List<DecisionEvent> add(String agent, String type, String jur, String lob, int n, int adversePerTen) {
        List<DecisionEvent> added = new ArrayList<>();
        String adverse = InsightsEngine.adverseOutcomeFor(type);
        String fine = "FRAUD_FLAG".equals(type) ? "CLEARED" : "APPROVED";
        for (int i = 0; i < n; i++) {
            boolean bad = (i % 10) < adversePerTen;
            Instant ts = NOW.minus(Duration.ofMinutes(10_000 - seq * 5L));
            DecisionEvent d = new DecisionEvent("d-" + (seq++), agent, type, bad ? adverse : fine, ts, jur,
                    bad ? GOOD_REASON : OK_REASON, lob, 4000.0, 0.85);
            decisions.add(d);
            added.add(d);
        }
        return added;
    }

    private void review(DecisionEvent d, boolean overturned) {
        verdicts.put(d.decisionId(), new ReviewVerdict(d.decisionId(),
                overturned ? ReviewVerdict.OVERTURNED : ReviewVerdict.UPHELD, "test", null, NOW));
    }

    private InsightsReport run() {
        return engine.analyse(decisions, Set.of(), verdicts, NOW);
    }

    private static AgentInsight agent(InsightsReport r, String id) {
        return r.agents().stream().filter(a -> a.agentId().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void emptyHistoryProducesAnEmptyReportWithoutFailing() {
        InsightsReport r = run();
        assertEquals(0, r.summary().decisionsAnalysed());
        assertTrue(r.agents().isEmpty());
        assertTrue(r.findings().isEmpty());
        assertEquals(4, r.calibration().size());
        assertNotNull(r.disclaimer());
    }

    @Test
    void agentDenyingFarMoreThanItsPeersIsAnOutlier() {
        add("claims-a", "CLAIMS", "US-CA", "PERSONAL_AUTO", 60, 5);
        add("claims-b", "CLAIMS", "US-CA", "PERSONAL_AUTO", 60, 1);

        InsightsReport r = run();

        AgentInsight a = agent(r, "claims-a");
        AgentInsight b = agent(r, "claims-b");
        assertTrue(a.outlier());
        assertEquals(0.5, a.adverseRate(), 1e-9);
        assertEquals(0.1, a.peerRate(), 1e-9);
        assertTrue(a.pVsPeers() < 0.001);
        assertFalse(b.outlier());
        assertTrue(a.riskScore() > b.riskScore());
        assertTrue(r.findings().stream().anyMatch(f -> "OUTLIER".equals(f.kind()) && "claims-a".equals(f.agentId())));
    }

    @Test
    void agentWhoseRecentBehaviourChangedIsFlaggedAsDrifting() {
        add("claims-a", "CLAIMS", "US-CA", "PERSONAL_AUTO", 50, 1);  // 10% denials historically
        add("claims-a", "CLAIMS", "US-CA", "PERSONAL_AUTO", 24, 6);  // then 60%
        add("claims-b", "CLAIMS", "US-CA", "PERSONAL_AUTO", 74, 1);

        InsightsReport r = run();

        AgentInsight a = agent(r, "claims-a");
        assertNotNull(a.drift());
        assertTrue(a.drift().drifting());
        assertEquals("UP", a.drift().direction());
        assertTrue(a.drift().recentRate() > a.drift().baselineRate() + 0.3);
        assertTrue(a.drift().psi() > 0.25);
        assertFalse(agent(r, "claims-b").drift().drifting());
        assertEquals(1, r.summary().driftingAgents());
        assertEquals("DRIFT", r.findings().get(0).kind());
        assertEquals("HIGH", r.findings().get(0).severity());
    }

    @Test
    void driftNeedsEnoughHistoryBeforeItIsComputed() {
        add("claims-a", "CLAIMS", "US-CA", "PERSONAL_AUTO", 15, 1);
        assertNull(agent(run(), "claims-a").drift());
    }

    @Test
    void segmentTreatedWorseIsCaughtByTheFourFifthsRule() {
        add("uw-a", "UNDERWRITING", "IN-MH", "HOMEOWNERS", 20, 6);
        for (String jur : List.of("US-CA", "US-NY", "EU-DE", "UK-LON")) {
            add("uw-a", "UNDERWRITING", jur, "HOMEOWNERS", 20, 1);
        }

        InsightsReport r = run();

        SegmentDisparity inMh = r.disparities().stream()
                .filter(s -> "uw-a".equals(s.agentId()) && "IN-MH".equals(s.segment()))
                .findFirst().orElseThrow();
        assertTrue(inMh.flagged());
        assertEquals(0.4 / 0.9, inMh.impactRatio(), 0.001);
        assertTrue(r.disparities().stream()
                .filter(s -> "uw-a".equals(s.agentId()) && !"IN-MH".equals(s.segment()))
                .noneMatch(SegmentDisparity::flagged));
        assertTrue(r.findings().stream().anyMatch(f -> "DISPARITY".equals(f.kind()) && "IN-MH".equals(f.segment())));
    }

    @Test
    void overturnRateComesFromHumanVerdictsAndIsComparedWithTheFleet() {
        List<DecisionEvent> bad = add("claims-a", "CLAIMS", "US-CA", "PERSONAL_AUTO", 40, 5);
        List<DecisionEvent> good = add("claims-b", "CLAIMS", "US-CA", "PERSONAL_AUTO", 40, 5);
        int i = 0;
        for (DecisionEvent d : bad) {
            if ("DENIED".equals(d.outcome())) {
                review(d, i++ % 2 == 0);      // 50% overturned
            }
        }
        i = 0;
        for (DecisionEvent d : good) {
            if ("DENIED".equals(d.outcome())) {
                review(d, i++ % 10 == 0);     // 10% overturned
            }
        }

        InsightsReport r = run();

        AgentInsight a = agent(r, "claims-a");
        assertEquals(20, a.reviewed());
        assertEquals(10, a.overturned());
        assertEquals(0.5, a.overturnRate(), 1e-9);
        assertTrue(a.overturnCiLow() < 0.5 && a.overturnCiHigh() > 0.5);
        assertTrue(a.overturnPVsRest() < 0.05);
        assertEquals(40, r.summary().reviewed());
        assertEquals(12, r.summary().overturned());
        assertTrue(r.findings().stream().anyMatch(f -> "OVERTURN".equals(f.kind()) && "claims-a".equals(f.agentId())));
        assertTrue(r.findings().stream().noneMatch(f -> "OVERTURN".equals(f.kind()) && "claims-b".equals(f.agentId())));
    }

    @Test
    void cleanAutonomousLineIsMarkedAsReadyToScale() {
        add("claims-a", "CLAIMS", "US-CA", "PERSONAL_AUTO", 40, 1);
        List<DecisionEvent> wc = add("claims-a", "CLAIMS", "US-CA", "WORKERS_COMP", 40, 1);
        Set<String> flagged = new java.util.HashSet<>();
        for (int i = 0; i < 20; i++) {
            flagged.add(wc.get(i).decisionId());   // half of workers' comp needed a person
        }

        InsightsReport r = engine.analyse(decisions, flagged, verdicts, NOW);

        LineInsight auto = r.portfolio().stream().filter(l -> "PERSONAL_AUTO".equals(l.lineOfBusiness())).findFirst().orElseThrow();
        LineInsight comp = r.portfolio().stream().filter(l -> "WORKERS_COMP".equals(l.lineOfBusiness())).findFirst().orElseThrow();
        assertEquals("SCALE", auto.posture());
        assertEquals(1.0, auto.automationRate(), 1e-9);
        assertEquals("WATCH", comp.posture());
        assertEquals(0.5, comp.humanTouchRate(), 1e-9);
        assertEquals(5.0, comp.reviewHours(), 1e-9);   // 20 cases × 15 min
    }

    @Test
    void riskComponentsAreWeightedIntoAScoreAndBand() {
        add("claims-a", "CLAIMS", "US-CA", "PERSONAL_AUTO", 50, 1);
        add("claims-a", "CLAIMS", "US-CA", "PERSONAL_AUTO", 24, 7);
        add("claims-b", "CLAIMS", "US-CA", "PERSONAL_AUTO", 74, 1);

        InsightsReport r = run();
        AgentInsight a = agent(r, "claims-a");

        double expected = 0;
        for (Map.Entry<String, Double> w : InsightsEngine.WEIGHTS.entrySet()) {
            expected += w.getValue() * a.riskComponents().get(w.getKey());
        }
        assertEquals(Math.round(expected * 100), a.riskScore());
        assertFalse("NORMAL".equals(a.riskBand()));
        assertEquals("NORMAL", agent(r, "claims-b").riskBand());
        assertEquals("claims-a", r.agents().get(0).agentId(), "agents are ranked by risk");
    }
}
