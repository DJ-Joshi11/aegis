package com.aegis.ingestion;

import com.aegis.contracts.DecisionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Stands in for a real Guidewire Qusar agent feed, which we don't have
 * sandbox access to (see the main README's "How we're using Qusar"
 * section). Every decision goes through the same
 * {@link DecisionIngestionService} a real feed would use, so the rest of
 * the pipeline (audit log, detection, evidence mapping, insights) can't tell
 * the difference.
 *
 * <p>Each simulated agent has a <b>behaviour profile</b>, so the insights
 * engine has realistic patterns to find. These are deliberately planted and
 * documented here so the team can explain them — the data is synthetic, the
 * analysis that finds them is real:
 * <ul>
 *   <li><b>qsr-claims-agent-07 — drift.</b> Healthy for the first ~60% of the
 *   history, then a simulated "severity model v2" update makes it deny about
 *   half its claims, with high self-reported confidence. Its lifetime denial
 *   rate stays under the old 50% rule, so only the drift test catches it.</li>
 *   <li><b>qsr-underwriting-agent-04 — segment disparity + thin reasoning.</b>
 *   Denies applicants in IN-MH far more often than anywhere else, and
 *   sometimes logs a one-line reasoning trail with no evidence in it.</li>
 *   <li><b>qsr-fraud-agent-03 — concentration.</b> Flags workers' comp claims
 *   much more often than other lines, and occasionally clears a claim at
 *   low confidence instead of referring it.</li>
 *   <li><b>qsr-underwriting-agent-01, qsr-claims-agent-02 — healthy controls.</b></li>
 * </ul>
 *
 * <p>On startup it back-fills {@code aegis.ingestion.backfill-count}
 * decisions spread over the past {@code aegis.ingestion.backfill-hours}
 * hours (the H2 database is in-memory and resets on every restart, so
 * without this the analytics would start empty), then keeps generating one
 * live decision every {@code aegis.ingestion.mock-interval-ms} (default 8s).
 */
@Component
public class MockDecisionGenerator {

    private static final Logger log = LoggerFactory.getLogger(MockDecisionGenerator.class);

    /** Fraction of the back-fill history after which agent-07's "model update" kicks in. */
    static final double DRIFT_POINT = 0.62;

    private final DecisionIngestionService ingestionService;
    private final Random random;
    private final int backfillCount;
    private final int backfillHours;

    public MockDecisionGenerator(DecisionIngestionService ingestionService,
                                 @Value("${aegis.ingestion.backfill-count:320}") int backfillCount,
                                 @Value("${aegis.ingestion.backfill-hours:72}") int backfillHours,
                                 @Value("${aegis.ingestion.seed:3}") long seed) {
        this.ingestionService = ingestionService;
        // Fixed seed → the back-filled history (and the patterns the insights page finds) is the same on every restart.
        this.random = new Random(seed);
        this.backfillCount = Math.max(0, backfillCount);
        this.backfillHours = Math.max(1, backfillHours);
    }

    private static final List<String> JURISDICTIONS =
            List.of("US-CA", "US-NY", "US-TX", "EU-DE", "EU-FR", "IN-MH", "UK-LON");

    /** Agent id → relative share of decision volume. */
    private static final Map<String, Integer> AGENT_WEIGHTS = Map.of(
            "qsr-underwriting-agent-01", 22,
            "qsr-underwriting-agent-04", 22,
            "qsr-claims-agent-02", 22,
            "qsr-claims-agent-07", 22,
            "qsr-fraud-agent-03", 12
    );
    private static final List<String> AGENT_ORDER = List.of(
            "qsr-underwriting-agent-01", "qsr-underwriting-agent-04",
            "qsr-claims-agent-02", "qsr-claims-agent-07", "qsr-fraud-agent-03");

    /** On startup: back-fill a few days of history so the insights page has something to analyse. */
    @EventListener(ApplicationReadyEvent.class)
    public void backfillHistory() {
        if (backfillCount == 0) {
            return;
        }
        Instant now = Instant.now();
        long spanMs = Duration.ofHours(backfillHours).toMillis();
        int ingested = 0;
        for (int i = 0; i < backfillCount; i++) {
            double progress = (double) i / backfillCount;
            long jitter = (long) (random.nextDouble() * spanMs / backfillCount);
            Instant ts = now.minusMillis((long) (spanMs * (1 - progress)) - jitter).minusSeconds(60);
            try {
                ingestionService.ingest(buildDecision(ts, progress >= DRIFT_POINT));
                ingested++;
            } catch (RuntimeException ex) {
                log.warn("Back-fill decision rejected: {}", ex.getMessage());
            }
        }
        log.info("Back-filled {} simulated Qusar decisions over the last {}h", ingested, backfillHours);
    }

    @Scheduled(fixedRateString = "${aegis.ingestion.mock-interval-ms:8000}",
               initialDelayString = "${aegis.ingestion.mock-initial-delay-ms:8000}")
    public void generateDecision() {
        ingestionService.ingest(buildDecision(Instant.now(), true));
    }

    /** Builds one decision; {@code drifted} switches agent-07 to its post-update behaviour. */
    DecisionEvent buildDecision(Instant timestamp, boolean drifted) {
        String agent = pickAgent();
        // agent-04 writes an India-heavy book, so IN-MH appears more often in its decisions
        String jurisdiction = "qsr-underwriting-agent-04".equals(agent) && random.nextDouble() < 0.25
                ? "IN-MH" : pick(JURISDICTIONS);
        return switch (agent) {
            case "qsr-underwriting-agent-01" -> underwriting(agent, timestamp, jurisdiction, false);
            case "qsr-underwriting-agent-04" -> underwriting(agent, timestamp, jurisdiction, true);
            case "qsr-claims-agent-02" -> claims(agent, timestamp, jurisdiction, false);
            case "qsr-claims-agent-07" -> claims(agent, timestamp, jurisdiction, drifted);
            default -> fraud(agent, timestamp, jurisdiction);
        };
    }

    private DecisionEvent underwriting(String agent, Instant ts, String jur, boolean biased) {
        String lob = biased
                ? pick(List.of("HOMEOWNERS", "HOMEOWNERS", "COMMERCIAL_PROPERTY"))
                : pick(List.of("HOMEOWNERS", "PERSONAL_AUTO", "PERSONAL_AUTO"));
        double sumInsured = sumInsured(lob);
        double denyP = biased ? ("IN-MH".equals(jur) ? 0.58 : 0.16) : 0.17;
        double referP = 0.20;
        double conf = clamp(gaussian(biased ? 0.74 : 0.83, biased ? 0.12 : 0.07));
        double r = random.nextDouble();

        String outcome;
        String reason;
        if (r < denyP) {
            outcome = "DENIED";
            reason = biased && random.nextDouble() < 0.35
                    ? "Auto-decision per model score."
                    : String.format("Credit score %d below 650 threshold; risk score %.2f exceeds appetite; auto-denied per policy UW-114.",
                        560 + random.nextInt(85), 0.72 + random.nextDouble() * 0.2);
        } else if (r < denyP + referP) {
            outcome = "REFERRED_TO_HUMAN";
            conf = clamp(Math.min(conf, 0.55 + random.nextDouble() * 0.1));
            reason = "COMMERCIAL_PROPERTY".equals(lob)
                    ? String.format("Sum insured $%,.0f above auto-bind authority; confidence %.2f below auto-decision threshold, referred to human underwriter.", sumInsured, conf)
                    : "Property in flood zone AE with no prior claims data; confidence below auto-decision threshold, referred to human underwriter.";
        } else {
            outcome = "APPROVED";
            reason = biased && random.nextDouble() < 0.12
                    ? "Approved per model."
                    : String.format("Applicant risk score %.2f, within carrier appetite band; sum insured $%,.0f; auto-approved per policy UW-108.",
                        0.35 + random.nextDouble() * 0.3, sumInsured);
        }
        return event(agent, "UNDERWRITING", outcome, ts, jur, reason, lob, sumInsured, conf);
    }

    private DecisionEvent claims(String agent, Instant ts, String jur, boolean drifted) {
        String lob = pick(List.of("PERSONAL_AUTO", "PERSONAL_AUTO", "HOMEOWNERS", "COMMERCIAL_PROPERTY", "WORKERS_COMP"));
        double amount = claimAmount(lob);
        double denyP = drifted ? 0.50 : 0.16;
        double partialP = drifted ? 0.16 : 0.24;
        double conf = clamp(gaussian(0.84, 0.07));
        double r = random.nextDouble();

        String outcome;
        String reason;
        if (r < denyP) {
            outcome = "DENIED";
            if (drifted && random.nextDouble() < 0.75) {
                conf = clamp(gaussian(0.91, 0.04));
                reason = String.format("Severity model v2 score %.2f exceeds auto-deny cutoff 0.70; claim of $%,.0f denied.",
                        0.70 + random.nextDouble() * 0.12, amount);
            } else {
                reason = String.format("Loss occurred %d days after policy lapse; no coverage in force at time of loss; claim of $%,.0f denied.",
                        3 + random.nextInt(20), amount);
            }
        } else if (r < denyP + partialP) {
            outcome = "PARTIAL_APPROVAL";
            double limit = Math.round(amount * (0.4 + random.nextDouble() * 0.35) / 500.0) * 500.0;
            reason = String.format("Estimate $%,.0f exceeds policy sublimit; approved up to sublimit of $%,.0f, balance denied.", amount, limit);
        } else {
            outcome = "APPROVED";
            reason = String.format("Damage estimate $%,.0f consistent with submitted photos and adjuster notes; approved in full.", amount);
        }
        return event(agent, "CLAIMS", outcome, ts, jur, reason, lob, amount, conf);
    }

    private DecisionEvent fraud(String agent, Instant ts, String jur) {
        String lob = pick(List.of("PERSONAL_AUTO", "HOMEOWNERS", "WORKERS_COMP", "WORKERS_COMP"));
        double amount = claimAmount(lob);
        double flagP = "WORKERS_COMP".equals(lob) ? 0.42 : 0.14;
        double conf = clamp(gaussian(0.74, 0.11));
        String outcome;
        String reason;
        if (random.nextDouble() < flagP) {
            outcome = "FLAGGED";
            reason = "WORKERS_COMP".equals(lob)
                    ? String.format("Injury reported %d days after employment start; treating provider linked to %d prior claims; $%,.0f claim flagged for SIU review.",
                        2 + random.nextInt(10), 2 + random.nextInt(4), amount)
                    : String.format("Claim filed %d days after policy inception; similar loss pattern to %d prior claims by same claimant; flagged for SIU review.",
                        1 + random.nextInt(6), 2 + random.nextInt(3));
        } else {
            outcome = "CLEARED";
            if (random.nextDouble() < 0.15) {
                conf = clamp(0.45 + random.nextDouble() * 0.12);
            }
            reason = "Claimant history and loss circumstances consistent with baseline; no fraud indicators found, cleared.";
        }
        return event(agent, "FRAUD_FLAG", outcome, ts, jur, reason, lob, amount, conf);
    }

    private static DecisionEvent event(String agent, String type, String outcome, Instant ts, String jur,
                                       String reason, String lob, double amount, double conf) {
        return new DecisionEvent(UUID.randomUUID().toString(), agent, type, outcome, ts, jur, reason,
                lob, Math.round(amount * 100.0) / 100.0, Math.round(conf * 1000.0) / 1000.0);
    }

    private double sumInsured(String lob) {
        return switch (lob) {
            case "PERSONAL_AUTO" -> logUniform(15_000, 60_000);
            case "COMMERCIAL_PROPERTY" -> logUniform(500_000, 4_000_000);
            default -> logUniform(150_000, 650_000);
        };
    }

    private double claimAmount(String lob) {
        return switch (lob) {
            case "PERSONAL_AUTO" -> logUniform(1_500, 25_000);
            case "HOMEOWNERS" -> logUniform(2_000, 60_000);
            case "COMMERCIAL_PROPERTY" -> logUniform(10_000, 250_000);
            case "WORKERS_COMP" -> logUniform(3_000, 80_000);
            default -> logUniform(1_000, 20_000);
        };
    }

    private double logUniform(double lo, double hi) {
        return Math.round(lo * Math.exp(random.nextDouble() * Math.log(hi / lo)));
    }

    private double gaussian(double mean, double sd) {
        return mean + random.nextGaussian() * sd;
    }

    private static double clamp(double v) {
        return Math.max(0.05, Math.min(0.99, v));
    }

    private String pickAgent() {
        int total = AGENT_WEIGHTS.values().stream().mapToInt(Integer::intValue).sum();
        int r = random.nextInt(total);
        for (String agent : AGENT_ORDER) {
            r -= AGENT_WEIGHTS.get(agent);
            if (r < 0) {
                return agent;
            }
        }
        return AGENT_ORDER.get(0);
    }

    private <T> T pick(List<T> options) {
        return options.get(random.nextInt(options.size()));
    }
}
