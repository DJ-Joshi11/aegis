package com.aegis.insights;

import com.aegis.contracts.DecisionEvent;
import com.aegis.ingestion.DecisionIngestionService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * DEMO ONLY — stands in for the carrier's human reviewers (claim appeals,
 * QA sampling, SIU investigations), the same way {@code MockDecisionGenerator}
 * stands in for Qusar. Without it the insights page would have no ground
 * truth to measure overturn rates against.
 *
 * It reviews adverse decisions (denials, partial approvals, fraud flags)
 * once they are older than {@code aegis.insights.sim-review-delay-seconds},
 * and deliberately leaves one in five untouched so the console's review
 * queue always has real work for a person to do.
 *
 * Its overturn probabilities mirror the behaviour profiles planted in
 * {@code MockDecisionGenerator}: the "severity model v2" denials, the IN-MH
 * underwriting denials and evidence-free reasoning are reversed far more
 * often. A real deployment deletes this class and posts verdicts to
 * {@code POST /api/reviews/{decisionId}} from the carrier's review workflow.
 * Disable with {@code aegis.insights.simulated-reviewer=false}.
 */
@Component
@ConditionalOnProperty(name = "aegis.insights.simulated-reviewer", havingValue = "true", matchIfMissing = true)
public class SimulatedReviewer {

    static final String NAME = "simulated-reviewer";

    private final DecisionIngestionService ingestionService;
    private final ReviewVerdictRepository repository;
    private final Duration delay;

    public SimulatedReviewer(DecisionIngestionService ingestionService, ReviewVerdictRepository repository,
                             @org.springframework.beans.factory.annotation.Value("${aegis.insights.sim-review-delay-seconds:90}") long delaySeconds) {
        this.ingestionService = ingestionService;
        this.repository = repository;
        this.delay = Duration.ofSeconds(delaySeconds);
    }

    @Scheduled(initialDelayString = "${aegis.insights.sim-review-initial-delay-ms:4000}",
               fixedDelayString = "${aegis.insights.sim-review-interval-ms:10000}")
    public void reviewBacklog() {
        Instant now = Instant.now();
        Instant cutoff = now.minus(delay);
        Set<String> alreadyReviewed = repository.findAll().stream()
                .map(ReviewVerdict::getDecisionId).collect(Collectors.toSet());
        int done = 0;
        for (DecisionEvent d : ingestionService.getAllDecisions()) {
            if (done >= 500) {
                break;
            }
            if (!InsightsService.isReviewable(d) || d.timestamp().isAfter(cutoff)
                    || leftForHumans(d.decisionId()) || alreadyReviewed.contains(d.decisionId())) {
                continue;
            }
            Random rnd = new Random(d.decisionId().hashCode());
            boolean overturn = rnd.nextDouble() < overturnProbability(d);
            Instant reviewedAt = d.timestamp().plus(Duration.ofMinutes(20 + rnd.nextInt(240)));
            if (reviewedAt.isAfter(now)) {
                reviewedAt = now;
            }
            repository.save(new ReviewVerdict(d.decisionId(),
                    overturn ? ReviewVerdict.OVERTURNED : ReviewVerdict.UPHELD, NAME,
                    overturn ? "Reversed on review (simulated appeal / QA outcome)" : "Decision stands (simulated review)",
                    reviewedAt));
            done++;
        }
    }

    /** One in five adverse decisions is never auto-reviewed, so the console's queue has work in it. */
    static boolean leftForHumans(String decisionId) {
        return Math.floorMod(decisionId.hashCode(), 5) == 0;
    }

    /** Probability a human would reverse this decision — encodes the planted behaviour profiles. */
    static double overturnProbability(DecisionEvent d) {
        String reason = d.reasoningTrail() == null ? "" : d.reasoningTrail().toLowerCase(Locale.ROOT);
        double p;
        if ("FLAGGED".equals(d.outcome())) {
            p = "WORKERS_COMP".equals(d.lineOfBusiness()) ? 0.24 : 0.12;
        } else if ("PARTIAL_APPROVAL".equals(d.outcome())) {
            p = 0.12;
        } else if (reason.contains("severity model v2")) {
            p = 0.58;
        } else if ("UNDERWRITING".equals(d.decisionType()) && "IN-MH".equals(d.jurisdiction())
                && "qsr-underwriting-agent-04".equals(d.agentId())) {
            p = 0.45;
        } else if (reason.length() < ReasoningQualityChecker.MIN_LENGTH) {
            p = 0.40;
        } else {
            p = 0.08;
        }
        if (d.confidence() != null && d.confidence() < 0.6) {
            p += 0.15;
        }
        return Math.min(0.95, p);
    }
}
