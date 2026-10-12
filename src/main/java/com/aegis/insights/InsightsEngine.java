package com.aegis.insights;

import com.aegis.contracts.DecisionEvent;
import com.aegis.insights.InsightsReport.AgentInsight;
import com.aegis.insights.InsightsReport.Assumptions;
import com.aegis.insights.InsightsReport.CalibrationBin;
import com.aegis.insights.InsightsReport.Drift;
import com.aegis.insights.InsightsReport.Finding;
import com.aegis.insights.InsightsReport.LineInsight;
import com.aegis.insights.InsightsReport.ReasoningExample;
import com.aegis.insights.InsightsReport.ReasoningSummary;
import com.aegis.insights.InsightsReport.SegmentDisparity;
import com.aegis.insights.InsightsReport.Signal;
import com.aegis.insights.InsightsReport.Summary;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The statistical inference engine. A pure function: decisions + which ones
 * detection flagged + human review verdicts in, {@link InsightsReport} out.
 * No Spring, no database, no randomness — so every number on the insights
 * page is reproducible and unit-testable.
 *
 * <p>Six analyses, each a classical, explainable technique:
 * <ol>
 *   <li><b>Peer outlier test</b> — each agent's adverse-outcome rate vs. the
 *   other agents doing the same job (one-sided two-proportion z-test, Wilson CI).</li>
 *   <li><b>Drift test</b> — each agent's recent window vs. its own history
 *   (two-proportion z-test on the adverse rate + Population Stability Index
 *   on the whole outcome mix).</li>
 *   <li><b>Segment disparity</b> — adverse-outcome rate per jurisdiction and
 *   line of business, with the four-fifths (80%) impact-ratio rule.</li>
 *   <li><b>Human-review ground truth</b> — overturn rate of reviewed decisions,
 *   as the closest available estimate of an agent's error rate.</li>
 *   <li><b>Confidence calibration</b> — does the agent's self-reported
 *   confidence actually predict which decisions get overturned?</li>
 *   <li><b>Reasoning quality</b> — rule checks on the logged reasoning trail
 *   ({@link ReasoningQualityChecker}).</li>
 * </ol>
 * Results are combined into a transparent, weighted 0–100 agent risk score
 * and a ranked list of plain-English findings.
 */
final class InsightsEngine {

    /** Tunable thresholds; defaults live in {@code application.properties}. */
    record Config(int minSample, double alpha, double reviewMinutesPerCase,
                  double impactRatioThreshold, double psiThreshold,
                  double lowConfidenceThreshold, int timelineBuckets) {
        static Config defaults() {
            return new Config(12, 0.01, 15, 0.8, 0.25, 0.6, 12);
        }
    }

    static final int HIGH_RISK = 45;
    static final int ELEVATED_RISK = 25;
    static final double DRIFT_ALPHA = 0.05;
    static final double DRIFT_MIN_DELTA = 0.12;
    static final int MIN_SEGMENT = 8;
    static final int MIN_REVIEWS = 8;
    /** Significance level for review-verdict signals (smaller samples than the outlier tests). */
    static final double SIGNAL_ALPHA = 0.05;

    /** Risk-score weights — shown on the page so the score is never a black box. */
    static final Map<String, Double> WEIGHTS = orderedMap(
            "peerOutlier", 0.30, "drift", 0.25, "overturn", 0.25, "reasoning", 0.10, "lowConfidence", 0.10);

    static final String DISCLAIMER = "Statistical signals for human review. AEGIS does not price risk, set premiums, "
            + "decide coverage, certify compliance or forecast profit. Figures are computed from the decisions AEGIS has "
            + "observed; in this demo those decisions are simulated.";

    private final Config cfg;
    private final ReasoningQualityChecker reasoningChecker;

    InsightsEngine(Config cfg) {
        this.cfg = cfg;
        this.reasoningChecker = new ReasoningQualityChecker(cfg.lowConfidenceThreshold());
    }

    /** The outcome that counts as "adverse to the customer" for each decision type. */
    static String adverseOutcomeFor(String decisionType) {
        return "FRAUD_FLAG".equals(decisionType) ? "FLAGGED" : "DENIED";
    }

    static boolean isAdverse(DecisionEvent d) {
        return adverseOutcomeFor(d.decisionType()).equals(d.outcome());
    }

    static String lobOf(DecisionEvent d) {
        return d.lineOfBusiness() == null || d.lineOfBusiness().isBlank() ? "UNSPECIFIED" : d.lineOfBusiness();
    }

    InsightsReport analyse(List<DecisionEvent> decisions, Set<String> flaggedIds,
                           Map<String, ReviewVerdict> verdicts, Instant now) {
        List<DecisionEvent> all = decisions.stream()
                .filter(d -> d.agentId() != null && d.timestamp() != null)
                .sorted(Comparator.comparing(DecisionEvent::timestamp))
                .toList();

        Map<String, List<String>> issuesById = new HashMap<>();
        for (DecisionEvent d : all) {
            issuesById.put(d.decisionId(), reasoningChecker.check(d));
        }

        List<Instant> bucketStarts = bucketStarts(all, now);
        List<AgentInsight> agents = analyseAgents(all, verdicts, issuesById, bucketStarts, now);
        List<SegmentDisparity> disparities = analyseDisparities(all);
        List<LineInsight> portfolio = analysePortfolio(all, flaggedIds, verdicts);
        List<CalibrationBin> calibration = analyseCalibration(all, verdicts);
        ReasoningSummary reasoning = summariseReasoning(all, issuesById);
        List<Finding> findings = findings(agents, disparities, portfolio, calibration);

        int reviewed = 0;
        int overturned = 0;
        double overturnedValue = 0;
        for (DecisionEvent d : all) {
            ReviewVerdict v = verdicts.get(d.decisionId());
            if (v != null) {
                reviewed++;
                if (v.isOverturned()) {
                    overturned++;
                    if (!"UNDERWRITING".equals(d.decisionType()) && d.amount() != null) {
                        overturnedValue += d.amount();
                    }
                }
            }
        }
        double[] ci = Stats.wilson(overturned, reviewed, Stats.Z95);
        int humanTouch = (int) all.stream().filter(d -> needsHuman(d, flaggedIds, verdicts)).count();
        int autonomous = (int) all.stream()
                .filter(d -> !"REFERRED_TO_HUMAN".equals(d.outcome()) && !flaggedIds.contains(d.decisionId()))
                .count();

        Summary summary = new Summary(
                all.size(),
                agents.size(),
                (int) agents.stream().filter(a -> !"NORMAL".equals(a.riskBand())).count(),
                (int) agents.stream().filter(a -> a.drift() != null && a.drift().drifting() && "UP".equals(a.drift().direction())).count(),
                reviewed,
                overturned,
                rate(overturned, reviewed),
                Stats.round(ci[0], 4),
                Stats.round(ci[1], 4),
                Stats.round(humanTouch * cfg.reviewMinutesPerCase() / 60.0, 1),
                rate(autonomous, all.size()),
                reasoning.issueRate(),
                Stats.round(overturnedValue, 0),
                (int) findings.stream().filter(f -> "HIGH".equals(f.severity())).count()
        );

        Assumptions assumptions = new Assumptions(cfg.minSample(), cfg.alpha(), cfg.reviewMinutesPerCase(),
                cfg.impactRatioThreshold(), cfg.psiThreshold(), cfg.lowConfidenceThreshold(), "USD-equivalent");

        return new InsightsReport(now, summary, agents, disparities, portfolio, calibration, reasoning,
                findings, bucketStarts, assumptions, DISCLAIMER);
    }

    // ------------------------------------------------------------------ agents

    private List<AgentInsight> analyseAgents(List<DecisionEvent> all, Map<String, ReviewVerdict> verdicts,
                                             Map<String, List<String>> issuesById, List<Instant> buckets, Instant now) {
        Map<String, List<DecisionEvent>> byAgent = all.stream()
                .collect(Collectors.groupingBy(DecisionEvent::agentId, TreeMap::new, Collectors.toList()));

        int fleetReviewed = 0;
        int fleetOverturned = 0;
        for (DecisionEvent d : all) {
            ReviewVerdict v = verdicts.get(d.decisionId());
            if (v != null) {
                fleetReviewed++;
                if (v.isOverturned()) {
                    fleetOverturned++;
                }
            }
        }

        List<AgentInsight> out = new ArrayList<>();
        for (Map.Entry<String, List<DecisionEvent>> e : byAgent.entrySet()) {
            String agentId = e.getKey();
            List<DecisionEvent> mine = e.getValue();
            String type = mode(mine, DecisionEvent::decisionType);
            String adverseOutcome = adverseOutcomeFor(type);
            int n = mine.size();
            int k = (int) mine.stream().filter(d -> adverseOutcome.equals(d.outcome())).count();
            double[] ci = Stats.wilson(k, n, Stats.Z95);

            // 1. Peer outlier test
            List<DecisionEvent> peers = all.stream()
                    .filter(d -> type.equals(d.decisionType()) && !agentId.equals(d.agentId()))
                    .toList();
            Double peerRate = null;
            Double z = null;
            Double p = null;
            boolean outlier = false;
            if (peers.size() >= cfg.minSample()) {
                int kp = (int) peers.stream().filter(d -> adverseOutcome.equals(d.outcome())).count();
                peerRate = rate(kp, peers.size());
                double zz = Stats.twoProportionZ(k, n, kp, peers.size());
                z = Stats.round(zz, 2);
                p = Stats.round(Stats.pUpper(zz), 4);
                outlier = n >= cfg.minSample() && p < cfg.alpha() && (double) k / n > peerRate;
            }

            // 2. Drift test
            Drift drift = drift(mine, adverseOutcome);

            // 3. Ground truth from human reviews
            int reviewed = 0;
            int overturned = 0;
            for (DecisionEvent d : mine) {
                ReviewVerdict v = verdicts.get(d.decisionId());
                if (v != null) {
                    reviewed++;
                    if (v.isOverturned()) {
                        overturned++;
                    }
                }
            }
            double[] oci = Stats.wilson(overturned, reviewed, Stats.Z95);
            Double overturnP = reviewed > 0 && fleetReviewed - reviewed > 0
                    ? Stats.round(Stats.pUpper(Stats.twoProportionZ(overturned, reviewed,
                        fleetOverturned - overturned, fleetReviewed - reviewed)), 4)
                    : null;

            // 4. Reasoning quality + confidence
            int withIssues = 0;
            int lowConf = 0;
            for (DecisionEvent d : mine) {
                List<String> issues = issuesById.getOrDefault(d.decisionId(), List.of());
                if (issues.stream().anyMatch(i -> !ReasoningQualityChecker.LOW_CONFIDENCE_AUTO.equals(i))) {
                    withIssues++;
                }
                if (issues.contains(ReasoningQualityChecker.LOW_CONFIDENCE_AUTO)) {
                    lowConf++;
                }
            }
            Double avgConf = mine.stream().filter(d -> d.confidence() != null)
                    .mapToDouble(DecisionEvent::confidence).average().stream().boxed().findFirst()
                    .map(v -> Stats.round(v, 3)).orElse(null);

            // 5. Composite risk score
            double reasoningRate = (double) withIssues / n;
            double lowConfRate = (double) lowConf / n;
            Map<String, Double> components = new LinkedHashMap<>();
            components.put("peerOutlier", z != null && n >= cfg.minSample() && (double) k / n > peerRate
                    ? Stats.round(Stats.clamp01(z / 4.0), 3) : 0.0);
            components.put("drift", drift != null && "UP".equals(drift.direction())
                    ? Stats.round(Math.max(Stats.clamp01(Math.abs(drift.z()) / 4.0), Stats.clamp01(drift.psi() / 0.5)), 3) : 0.0);
            // Scored on the LOWER bound of the Wilson interval, so a handful of reviews can't inflate risk.
            components.put("overturn", reviewed >= 5 ? Stats.round(Stats.clamp01(oci[0] / 0.3), 3) : 0.0);
            components.put("reasoning", Stats.round(Stats.clamp01(reasoningRate / 0.25), 3));
            components.put("lowConfidence", Stats.round(Stats.clamp01(lowConfRate / 0.25), 3));
            double score = 0;
            for (Map.Entry<String, Double> w : WEIGHTS.entrySet()) {
                score += w.getValue() * components.get(w.getKey());
            }
            int riskScore = (int) Math.round(score * 100);
            String band = riskScore >= HIGH_RISK ? "HIGH" : riskScore >= ELEVATED_RISK ? "ELEVATED" : "NORMAL";

            // 6. Timeline of the adverse rate
            List<Double> timeline = new ArrayList<>();
            List<Integer> counts = new ArrayList<>();
            for (int b = 0; b < buckets.size(); b++) {
                Instant from = buckets.get(b);
                Instant to = b + 1 < buckets.size() ? buckets.get(b + 1) : now.plusSeconds(1);
                int bn = 0;
                int bk = 0;
                for (DecisionEvent d : mine) {
                    if (!d.timestamp().isBefore(from) && d.timestamp().isBefore(to)) {
                        bn++;
                        if (adverseOutcome.equals(d.outcome())) {
                            bk++;
                        }
                    }
                }
                counts.add(bn);
                timeline.add(bn >= 3 ? rate(bk, bn) : null);
            }

            Map<String, Integer> mix = new TreeMap<>();
            mine.forEach(d -> mix.merge(d.outcome(), 1, Integer::sum));

            out.add(new AgentInsight(agentId, type, n, adverseOutcome, k, rate(k, n),
                    Stats.round(ci[0], 4), Stats.round(ci[1], 4), peerRate, z, p, outlier, drift,
                    reviewed, overturned, rate(overturned, reviewed), Stats.round(oci[0], 4), Stats.round(oci[1], 4), overturnP,
                    Stats.round(reasoningRate, 4), Stats.round(lowConfRate, 4), avgConf,
                    riskScore, band, components, timeline, counts, mix));
        }
        out.sort(Comparator.comparingInt(AgentInsight::riskScore).reversed().thenComparing(AgentInsight::agentId));
        return out;
    }

    /** Recent window (last 30% of the agent's decisions, at least the min sample) vs. everything before it. */
    private Drift drift(List<DecisionEvent> mine, String adverseOutcome) {
        int n = mine.size();
        int recentN = Math.max(cfg.minSample(), (int) Math.round(n * 0.3));
        int baselineN = n - recentN;
        if (baselineN < cfg.minSample()) {
            return null;
        }
        List<DecisionEvent> baseline = mine.subList(0, baselineN);
        List<DecisionEvent> recent = mine.subList(baselineN, n);
        int kb = (int) baseline.stream().filter(d -> adverseOutcome.equals(d.outcome())).count();
        int kr = (int) recent.stream().filter(d -> adverseOutcome.equals(d.outcome())).count();
        double rb = (double) kb / baselineN;
        double rr = (double) kr / recentN;
        double z = Stats.twoProportionZ(kr, recentN, kb, baselineN);
        double p = Stats.pTwoSided(z);
        double psi = Stats.psi(countBy(baseline, DecisionEvent::outcome), countBy(recent, DecisionEvent::outcome));
        boolean drifting = (p < DRIFT_ALPHA && Math.abs(rr - rb) >= DRIFT_MIN_DELTA)
                || (psi >= cfg.psiThreshold() && p < 0.2);
        String direction = !drifting ? "FLAT" : rr > rb ? "UP" : "DOWN";
        return new Drift(baselineN, recentN, Stats.round(rb, 4), Stats.round(rr, 4),
                Stats.round(z, 2), Stats.round(p, 4), Stats.round(psi, 3), drifting, direction);
    }

    // ------------------------------------------------------------------ disparity

    private List<SegmentDisparity> analyseDisparities(List<DecisionEvent> all) {
        List<SegmentDisparity> out = new ArrayList<>();
        for (String type : List.of("UNDERWRITING", "CLAIMS")) {
            List<DecisionEvent> ofType = all.stream().filter(d -> type.equals(d.decisionType())).toList();
            out.addAll(disparity(null, type, "jurisdiction", ofType, DecisionEvent::jurisdiction));
            out.addAll(disparity(null, type, "lineOfBusiness", ofType, InsightsEngine::lobOf));
            Map<String, List<DecisionEvent>> byAgent = ofType.stream()
                    .collect(Collectors.groupingBy(DecisionEvent::agentId, TreeMap::new, Collectors.toList()));
            for (Map.Entry<String, List<DecisionEvent>> e : byAgent.entrySet()) {
                out.addAll(disparity(e.getKey(), type, "jurisdiction", e.getValue(), DecisionEvent::jurisdiction));
                out.addAll(disparity(e.getKey(), type, "lineOfBusiness", e.getValue(), InsightsEngine::lobOf));
            }
        }
        out.sort(Comparator.comparing(SegmentDisparity::flagged).reversed()
                .thenComparingDouble(SegmentDisparity::impactRatio));
        return out;
    }

    private List<SegmentDisparity> disparity(String agentId, String type, String dimension,
                                             List<DecisionEvent> rows, Function<DecisionEvent, String> key) {
        Map<String, List<DecisionEvent>> bySeg = rows.stream()
                .filter(d -> key.apply(d) != null)
                .collect(Collectors.groupingBy(key, TreeMap::new, Collectors.toList()));
        Map<String, List<DecisionEvent>> eligible = new TreeMap<>();
        bySeg.forEach((s, list) -> {
            if (list.size() >= MIN_SEGMENT) {
                eligible.put(s, list);
            }
        });
        if (eligible.size() < 2) {
            return List.of();
        }
        double bestFavourable = eligible.values().stream()
                .mapToDouble(list -> 1 - adverseShare(list)).max().orElse(1);
        int total = rows.size();
        int totalAdverse = (int) rows.stream().filter(InsightsEngine::isAdverse).count();

        List<SegmentDisparity> out = new ArrayList<>();
        for (Map.Entry<String, List<DecisionEvent>> e : eligible.entrySet()) {
            List<DecisionEvent> seg = e.getValue();
            int n = seg.size();
            int k = (int) seg.stream().filter(InsightsEngine::isAdverse).count();
            double adverse = (double) k / n;
            double favourable = 1 - adverse;
            double ratio = bestFavourable == 0 ? 1 : favourable / bestFavourable;
            double z = Stats.twoProportionZ(k, n, totalAdverse - k, total - n);
            double p = Stats.pUpper(z);
            boolean flagged = ratio < cfg.impactRatioThreshold() && p < cfg.alpha();
            out.add(new SegmentDisparity(agentId, type, dimension, e.getKey(), n, Stats.round(adverse, 4),
                    Stats.round(favourable, 4), Stats.round(ratio, 3), Stats.round(z, 2), Stats.round(p, 4), flagged));
        }
        return out;
    }

    private static double adverseShare(List<DecisionEvent> list) {
        return list.isEmpty() ? 0 : (double) list.stream().filter(InsightsEngine::isAdverse).count() / list.size();
    }

    // ------------------------------------------------------------------ portfolio

    private boolean needsHuman(DecisionEvent d, Set<String> flaggedIds, Map<String, ReviewVerdict> verdicts) {
        return "REFERRED_TO_HUMAN".equals(d.outcome()) || flaggedIds.contains(d.decisionId())
                || verdicts.containsKey(d.decisionId());
    }

    private List<LineInsight> analysePortfolio(List<DecisionEvent> all, Set<String> flaggedIds,
                                               Map<String, ReviewVerdict> verdicts) {
        Map<String, List<DecisionEvent>> byLine = all.stream()
                .collect(Collectors.groupingBy(InsightsEngine::lobOf, TreeMap::new, Collectors.toList()));
        if (byLine.isEmpty()) {
            return List.of();
        }

        int totalN = all.size();
        double avgHumanTouch = (double) all.stream().filter(d -> needsHuman(d, flaggedIds, verdicts)).count() / Math.max(1, totalN);
        List<DecisionEvent> decidable = all.stream().filter(d -> !"FRAUD_FLAG".equals(d.decisionType())).toList();
        double avgDenial = adverseShare(decidable);
        List<DecisionEvent> fraudAll = all.stream().filter(d -> "FRAUD_FLAG".equals(d.decisionType())).toList();
        double avgFraudFlag = adverseShare(fraudAll);

        int allReviewed = 0;
        int allOverturned = 0;
        for (DecisionEvent d : all) {
            ReviewVerdict v = verdicts.get(d.decisionId());
            if (v != null) {
                allReviewed++;
                if (v.isOverturned()) {
                    allOverturned++;
                }
            }
        }

        List<LineInsight> out = new ArrayList<>();
        for (Map.Entry<String, List<DecisionEvent>> e : byLine.entrySet()) {
            List<DecisionEvent> rows = e.getValue();
            int n = rows.size();
            Map<String, Integer> byType = countBy(rows, DecisionEvent::decisionType);
            int humanTouch = (int) rows.stream().filter(d -> needsHuman(d, flaggedIds, verdicts)).count();
            int autonomous = (int) rows.stream()
                    .filter(d -> !"REFERRED_TO_HUMAN".equals(d.outcome()) && !flaggedIds.contains(d.decisionId()))
                    .count();
            List<DecisionEvent> lineDecidable = rows.stream().filter(d -> !"FRAUD_FLAG".equals(d.decisionType())).toList();
            List<DecisionEvent> lineFraud = rows.stream().filter(d -> "FRAUD_FLAG".equals(d.decisionType())).toList();
            double denialRate = adverseShare(lineDecidable);
            double fraudRate = adverseShare(lineFraud);

            int reviewed = 0;
            int overturned = 0;
            double overturnedValue = 0;
            for (DecisionEvent d : rows) {
                ReviewVerdict v = verdicts.get(d.decisionId());
                if (v != null) {
                    reviewed++;
                    if (v.isOverturned()) {
                        overturned++;
                        if (!"UNDERWRITING".equals(d.decisionType())) {
                            overturnedValue += amount(d);
                        }
                    }
                }
            }
            double claimValue = rows.stream().filter(d -> "CLAIMS".equals(d.decisionType())).mapToDouble(InsightsEngine::amount).sum();
            double deniedValue = rows.stream().filter(d -> "CLAIMS".equals(d.decisionType()) && "DENIED".equals(d.outcome()))
                    .mapToDouble(InsightsEngine::amount).sum();
            double fraudValue = rows.stream().filter(d -> "FRAUD_FLAG".equals(d.decisionType()) && "FLAGGED".equals(d.outcome()))
                    .mapToDouble(InsightsEngine::amount).sum();
            double humanTouchRate = (double) humanTouch / n;
            double overturnRate = reviewed == 0 ? 0 : (double) overturned / reviewed;

            List<Signal> signals = new ArrayList<>();
            double overturnP = Stats.pUpper(Stats.twoProportionZ(overturned, reviewed,
                    allOverturned - overturned, allReviewed - reviewed));
            if (reviewed >= MIN_REVIEWS && overturnRate >= 0.2 && overturnP < SIGNAL_ALPHA) {
                signals.add(new Signal("HIGH", String.format(
                        "%s of reviewed adverse decisions were overturned by a person (portfolio %s) — AI errors are reaching customers here; %s of claim decisions reversed.",
                        pct(overturnRate), pct(allReviewed == 0 ? 0 : (double) allOverturned / allReviewed), money(overturnedValue))));
            }
            if (humanTouchRate >= 0.25 && humanTouchRate >= avgHumanTouch * 1.3) {
                signals.add(new Signal("WARN", String.format(
                        "%s of decisions needed a person (portfolio average %s) — the AI automates this line poorly, so reviewer cost concentrates here.",
                        pct(humanTouchRate), pct(avgHumanTouch))));
            }
            if (lineDecidable.size() >= MIN_SEGMENT && denialRate >= 0.25 && denialRate >= avgDenial * 1.5) {
                signals.add(new Signal("WARN", String.format(
                        "Denial rate %s vs %s across the portfolio — check whether appetite rules or the agent are driving it.",
                        pct(denialRate), pct(avgDenial))));
            }
            if (lineFraud.size() >= 5 && fraudRate >= 0.3 && fraudRate >= avgFraudFlag * 1.3) {
                signals.add(new Signal("WARN", String.format(
                        "Fraud referrals concentrated: %s of screened claims flagged (portfolio %s) — check SIU capacity and the false-positive rate.",
                        pct(fraudRate), pct(avgFraudFlag))));
            }
            String posture;
            if (signals.stream().anyMatch(s -> "HIGH".equals(s.level()))) {
                posture = "FIX_FIRST";
            } else if (!signals.isEmpty()) {
                posture = "WATCH";
            } else if (n >= cfg.minSample() && humanTouchRate <= avgHumanTouch && (reviewed < 5 || overturnRate <= 0.15)) {
                posture = "SCALE";
                signals.add(new Signal("GOOD", String.format(
                        "Stable and mostly autonomous: %s of decisions need no person and %s of reviewed decisions were overturned — the strongest candidate to scale AI automation.",
                        pct((double) autonomous / n), reviewed == 0 ? "none" : pct(overturnRate))));
            } else {
                posture = "STEADY";
            }

            out.add(new LineInsight(e.getKey(), n, byType, rate(autonomous, n), Stats.round(humanTouchRate, 4),
                    Stats.round(denialRate, 4), Stats.round(fraudRate, 4), reviewed, overturned, Stats.round(overturnRate, 4),
                    Stats.round(claimValue, 0), Stats.round(deniedValue, 0), Stats.round(overturnedValue, 0), Stats.round(fraudValue, 0),
                    Stats.round(humanTouch * cfg.reviewMinutesPerCase() / 60.0, 1), posture, signals));
        }
        Map<String, Integer> order = Map.of("FIX_FIRST", 0, "WATCH", 1, "STEADY", 2, "SCALE", 3);
        out.sort(Comparator.comparing((LineInsight l) -> order.get(l.posture())).thenComparing(LineInsight::decisions, Comparator.reverseOrder()));
        return out;
    }

    // ------------------------------------------------------------------ calibration

    private List<CalibrationBin> analyseCalibration(List<DecisionEvent> all, Map<String, ReviewVerdict> verdicts) {
        double[][] edges = {{0, 0.6}, {0.6, 0.75}, {0.75, 0.9}, {0.9, 1.0001}};
        String[] labels = {"< 0.60", "0.60 – 0.75", "0.75 – 0.90", "≥ 0.90"};
        List<CalibrationBin> out = new ArrayList<>();
        for (int i = 0; i < edges.length; i++) {
            double lo = edges[i][0];
            double hi = edges[i][1];
            int reviewed = 0;
            int overturned = 0;
            double confSum = 0;
            for (DecisionEvent d : all) {
                ReviewVerdict v = verdicts.get(d.decisionId());
                if (v == null || d.confidence() == null || d.confidence() < lo || d.confidence() >= hi) {
                    continue;
                }
                reviewed++;
                confSum += d.confidence();
                if (v.isOverturned()) {
                    overturned++;
                }
            }
            out.add(new CalibrationBin(labels[i], lo, Math.min(hi, 1.0), reviewed, overturned, rate(overturned, reviewed),
                    reviewed == 0 ? 0 : Stats.round(confSum / reviewed, 3)));
        }
        return out;
    }

    // ------------------------------------------------------------------ reasoning

    private ReasoningSummary summariseReasoning(List<DecisionEvent> all, Map<String, List<String>> issuesById) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String c : List.of(ReasoningQualityChecker.THIN, ReasoningQualityChecker.NO_EVIDENCE,
                ReasoningQualityChecker.CONTRADICTION, ReasoningQualityChecker.LOW_CONFIDENCE_AUTO)) {
            counts.put(c, 0);
        }
        int withIssues = 0;
        List<ReasoningExample> examples = new ArrayList<>();
        for (int i = all.size() - 1; i >= 0; i--) {
            DecisionEvent d = all.get(i);
            List<String> issues = issuesById.getOrDefault(d.decisionId(), List.of());
            if (issues.isEmpty()) {
                continue;
            }
            withIssues++;
            issues.forEach(c -> counts.merge(c, 1, Integer::sum));
            if (examples.size() < 8) {
                examples.add(new ReasoningExample(d.decisionId(), d.agentId(), d.outcome(), issues, d.reasoningTrail()));
            }
        }
        return new ReasoningSummary(all.size(), withIssues, rate(withIssues, all.size()), counts, examples);
    }

    // ------------------------------------------------------------------ findings

    private List<Finding> findings(List<AgentInsight> agents, List<SegmentDisparity> disparities,
                                   List<LineInsight> portfolio, List<CalibrationBin> calibration) {
        List<Finding> out = new ArrayList<>();
        for (AgentInsight a : agents) {
            String what = "FLAGGED".equals(a.adverseOutcome()) ? "fraud-flag" : "denial";
            Drift d = a.drift();
            if (d != null && d.drifting() && "UP".equals(d.direction())) {
                out.add(new Finding("HIGH", "DRIFT",
                        a.agentId() + " changed behaviour recently",
                        String.format("Its %s rate rose from %s to %s over its latest %d decisions — consistent with a model or configuration change. Review what changed before more decisions go out.",
                                what, pct(d.baselineRate()), pct(d.recentRate()), d.recentN()),
                        a.agentId(), null,
                        String.format("two-proportion z = %.2f, p = %s · PSI = %.2f", d.z(), pFmt(d.p()), d.psi())));
            } else if (d != null && d.drifting()) {
                out.add(new Finding("LOW", "DRIFT",
                        a.agentId() + " shifted toward fewer " + what + "s",
                        String.format("Its %s rate fell from %s to %s recently. Usually benign, but confirm it was intended.",
                                what, pct(d.baselineRate()), pct(d.recentRate())),
                        a.agentId(), null, String.format("z = %.2f, p = %s · PSI = %.2f", d.z(), pFmt(d.p()), d.psi())));
            }
            if (a.outlier()) {
                out.add(new Finding(a.pVsPeers() != null && a.pVsPeers() < 0.001 ? "HIGH" : "MEDIUM", "OUTLIER",
                        a.agentId() + " is an outlier against its peers",
                        String.format("%s %s rate vs %s for other agents doing the same job (95%% CI %s–%s).",
                                pct(a.adverseRate()), what, pct(a.peerRate()), pct(a.ciLow()), pct(a.ciHigh())),
                        a.agentId(), null,
                        String.format("one-sided z = %.2f, p = %s, n = %d", a.zVsPeers(), pFmt(a.pVsPeers()), a.decisions())));
            }
            if (a.reviewed() >= MIN_REVIEWS && a.overturnRate() >= 0.2
                    && a.overturnPVsRest() != null && a.overturnPVsRest() < SIGNAL_ALPHA) {
                out.add(new Finding(a.overturnRate() >= 0.35 ? "HIGH" : "MEDIUM", "OVERTURN",
                        "Reviewers keep reversing " + a.agentId(),
                        String.format("%d of %d reviewed decisions were overturned (%s, 95%% CI %s–%s) — the closest thing to a measured error rate.",
                                a.overturned(), a.reviewed(), pct(a.overturnRate()), pct(a.overturnCiLow()), pct(a.overturnCiHigh())),
                        a.agentId(), null, "Wilson score interval on human review verdicts"));
            }
            if (a.reasoningIssueRate() >= 0.1) {
                out.add(new Finding("MEDIUM", "REASONING",
                        a.agentId() + " logs reasoning a regulator couldn't accept",
                        String.format("%s of its decisions have a thin, evidence-free or contradictory reasoning trail. Under explainability rules each one is a disclosure gap.",
                                pct(a.reasoningIssueRate())),
                        a.agentId(), null, "rule checks: length < 40 chars · no figures on an adverse outcome · outcome contradicts text"));
            }
            if (a.lowConfidenceAutoRate() >= 0.08) {
                out.add(new Finding("MEDIUM", "CONFIDENCE",
                        a.agentId() + " decides alone when it isn't sure",
                        String.format("%s of its decisions were finalised at confidence below %.2f instead of being referred to a person.",
                                pct(a.lowConfidenceAutoRate()), cfg.lowConfidenceThreshold()),
                        a.agentId(), null, "agent self-reported confidence < threshold and outcome not referred/flagged"));
            }
        }

        for (SegmentDisparity s : disparities) {
            if (!s.flagged()) {
                continue;
            }
            String who = s.agentId() == null ? "All " + s.decisionType().toLowerCase() + " agents" : s.agentId();
            String dim = "jurisdiction".equals(s.dimension()) ? "jurisdiction" : "line of business";
            out.add(new Finding(s.impactRatio() < 0.7 ? "HIGH" : "MEDIUM", "DISPARITY",
                    who + (s.agentId() == null ? " treat " : " treats ") + s.segment() + " differently",
                    String.format("Adverse-outcome rate %s in %s %s; its favourable rate is %.0f%% of the best segment's, below the four-fifths (80%%) guideline.",
                            pct(s.adverseRate()), dim, s.segment(), s.impactRatio() * 100),
                    s.agentId(), s.segment(),
                    String.format("impact ratio = %.2f · one-sided z = %.2f, p = %s, n = %d", s.impactRatio(), s.z(), pFmt(s.p()), s.decisions())));
        }

        for (LineInsight l : portfolio) {
            for (Signal sig : l.signals()) {
                String sev = switch (sig.level()) {
                    case "HIGH" -> "HIGH";
                    case "WARN" -> "MEDIUM";
                    default -> "LOW";
                };
                String kind = "GOOD".equals(sig.level()) ? "OPPORTUNITY" : "PORTFOLIO";
                String title = "GOOD".equals(sig.level())
                        ? pretty(l.lineOfBusiness()) + ": AI is ready to scale"
                        : pretty(l.lineOfBusiness()) + ": " + (sig.text().startsWith("Fraud") ? "fraud referrals concentrated"
                        : sig.text().contains("needed a person") ? "automation gap" : sig.text().startsWith("Denial") ? "denials elevated" : "AI errors reaching customers");
                out.add(new Finding(sev, kind, title, sig.text(), null, l.lineOfBusiness(),
                        String.format("%d decisions · %d reviewed · %s reviewer hours", l.decisions(), l.reviewed(), trim1(l.reviewHours()))));
            }
        }

        CalibrationBin top = calibration.get(calibration.size() - 1);
        CalibrationBin low = calibration.stream().filter(b -> b.reviewed() >= 5).findFirst().orElse(null);
        int restReviewed = calibration.stream().filter(b -> b != top).mapToInt(CalibrationBin::reviewed).sum();
        int restOverturned = calibration.stream().filter(b -> b != top).mapToInt(CalibrationBin::overturned).sum();
        double calP = Stats.pUpper(Stats.twoProportionZ(top.overturned(), top.reviewed(), restOverturned, restReviewed));
        if (top.reviewed() >= MIN_REVIEWS && top.overturnRate() >= 0.2 && calP < SIGNAL_ALPHA) {
            boolean inverted = low != null && low != top && top.overturnRate() >= low.overturnRate();
            out.add(new Finding(inverted ? "HIGH" : "MEDIUM", "CALIBRATION",
                    "High confidence doesn't mean correct",
                    String.format("%s of decisions agents logged at confidence ≥ 0.90 were overturned on review%s. Confidence can't be used to decide what skips human review.",
                            pct(top.overturnRate()), inverted ? String.format(" — no better than the %s band (%s)", low.label(), pct(low.overturnRate())) : ""),
                    null, null, String.format("%d reviewed at ≥ 0.90 confidence · one-sided z-test vs lower bands, p = %s",
                            top.reviewed(), pFmt(calP))));
        }

        Map<String, Integer> sev = Map.of("HIGH", 0, "MEDIUM", 1, "LOW", 2);
        Map<String, Integer> kindOrder = Map.of("DRIFT", 0, "OVERTURN", 1, "DISPARITY", 2, "CALIBRATION", 3, "OUTLIER", 4,
                "PORTFOLIO", 5, "REASONING", 6, "CONFIDENCE", 7, "OPPORTUNITY", 8);
        out.sort(Comparator.comparing((Finding f) -> sev.get(f.severity()))
                .thenComparing(f -> kindOrder.getOrDefault(f.kind(), 9)));
        return out;
    }

    // ------------------------------------------------------------------ helpers

    private List<Instant> bucketStarts(List<DecisionEvent> all, Instant now) {
        int b = Math.max(1, cfg.timelineBuckets());
        Instant start = all.isEmpty() ? now.minusSeconds(3600) : all.get(0).timestamp();
        long span = Math.max(b, now.toEpochMilli() - start.toEpochMilli());
        List<Instant> out = new ArrayList<>();
        for (int i = 0; i < b; i++) {
            out.add(Instant.ofEpochMilli(start.toEpochMilli() + span * i / b));
        }
        return out;
    }

    private static double amount(DecisionEvent d) {
        return d.amount() == null ? 0 : d.amount();
    }

    private static double rate(int k, int n) {
        return n == 0 ? 0.0 : Stats.round((double) k / n, 4);
    }

    private static <T> Map<String, Integer> countBy(List<T> rows, Function<T, String> key) {
        Map<String, Integer> m = new TreeMap<>();
        rows.forEach(r -> m.merge(String.valueOf(key.apply(r)), 1, Integer::sum));
        return m;
    }

    private static String mode(List<DecisionEvent> rows, Function<DecisionEvent, String> key) {
        return countBy(rows, key).entrySet().stream().max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse("UNKNOWN");
    }

    private static String pct(Double v) {
        return v == null ? "—" : String.format("%.0f%%", v * 100);
    }

    private static String pFmt(Double p) {
        if (p == null) {
            return "—";
        }
        return p < 0.001 ? "< 0.001" : String.format("%.3f", p);
    }

    private static String money(double v) {
        if (v >= 1_000_000) {
            return String.format("$%.1fM", v / 1_000_000);
        }
        if (v >= 1_000) {
            return String.format("$%.0fK", v / 1_000);
        }
        return String.format("$%.0f", v);
    }

    private static String trim1(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    private static String pretty(String lob) {
        String s = lob.replace('_', ' ').toLowerCase();
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static Map<String, Double> orderedMap(Object... kv) {
        Map<String, Double> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], (Double) kv[i + 1]);
        }
        return java.util.Collections.unmodifiableMap(m);
    }
}
