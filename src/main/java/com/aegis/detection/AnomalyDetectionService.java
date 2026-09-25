package com.aegis.detection;

import com.aegis.contracts.DecisionEvent;
import com.aegis.contracts.FlaggedDecision;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The "Check" mechanic: reviews every ingested decision, flags the ones
 * that look wrong, and publishes the outcome as a {@link FlaggedDecision}
 * for the payout package.
 *
 * Two flagging rules, both simple by design (this demonstrates the
 * anomaly-detection concept, it isn't a real ML model):
 * <ol>
 *   <li><b>Always-flag:</b> a {@code FRAUD_FLAG} decision whose outcome is
 *   {@code FLAGGED} always routes to compliance review — that is what a
 *   fraud flag means.</li>
 *   <li><b>Rate-based outlier:</b> once an agent has a minimum sample of
 *   decisions, if its {@code DENIED} rate exceeds a threshold, its latest
 *   denial is flagged as an outlier worth a second look.</li>
 * </ol>
 */
@Service
public class AnomalyDetectionService {

    private static final int MIN_SAMPLE_SIZE = 5;
    private static final double DENIAL_RATE_THRESHOLD = 0.5;

    private final ComplianceRulesService complianceRulesService;
    private final FlaggedAuditRepository repository;
    private final ApplicationEventPublisher eventPublisher;
    private final int minSampleSize;
    private final double denialRateThreshold;

    private final Map<String, AgentStats> statsByAgent = new ConcurrentHashMap<>();

    public AnomalyDetectionService(ComplianceRulesService complianceRulesService,
                                    FlaggedAuditRepository repository,
                                    ApplicationEventPublisher eventPublisher,
                                    @Value("${aegis.detection.min-sample-size:5}") int minSampleSize,
                                    @Value("${aegis.detection.denial-rate-threshold:0.5}") double denialRateThreshold) {
        this.complianceRulesService = complianceRulesService;
        this.repository = repository;
        this.eventPublisher = eventPublisher;
        this.minSampleSize = minSampleSize > 0 ? minSampleSize : MIN_SAMPLE_SIZE;
        this.denialRateThreshold = denialRateThreshold > 0 ? denialRateThreshold : DENIAL_RATE_THRESHOLD;
    }

    @EventListener
    public void onDecisionIngested(DecisionEvent decision) {
        AgentStats stats = statsByAgent.computeIfAbsent(decision.agentId(), id -> new AgentStats());
        stats.total.incrementAndGet();
        boolean isDenial = "DENIED".equals(decision.outcome());
        if (isDenial) {
            stats.denials.incrementAndGet();
        }

        FlagOutcome outcome = evaluate(decision, stats, isDenial);

        String complianceStatus = outcome.flagged()
                ? complianceRulesService.requiredStandardFor(decision.jurisdiction())
                : complianceRulesService.notApplicableStatus();

        if (outcome.flagged()) {
            repository.save(new FlaggedAudit(
                    decision.decisionId(), decision.agentId(), decision.decisionType(), decision.outcome(),
                    decision.jurisdiction(), outcome.reason(), complianceStatus, Instant.now()));
        }

        eventPublisher.publishEvent(new FlaggedDecision(decision, outcome.flagged(), outcome.reason(), complianceStatus));
    }

    private FlagOutcome evaluate(DecisionEvent decision, AgentStats stats, boolean isDenial) {
        if ("FRAUD_FLAG".equals(decision.decisionType()) && "FLAGGED".equals(decision.outcome())) {
            return new FlagOutcome(true, "Fraud flag from " + decision.agentId() + " routes to compliance review by policy");
        }

        if (isDenial && stats.total.get() >= minSampleSize) {
            double rate = (double) stats.denials.get() / stats.total.get();
            if (rate > denialRateThreshold) {
                return new FlagOutcome(true, String.format(
                        "Denial rate for %s is %.0f%% over %d decisions, exceeds the %.0f%% threshold",
                        decision.agentId(), rate * 100, stats.total.get(), denialRateThreshold * 100));
            }
        }

        return new FlagOutcome(false, null);
    }

    /** All decisions detection has flagged, most recent first. */
    public List<FlaggedAudit> getAllFlagged() {
        return repository.findAllByOrderByReviewedAtDesc();
    }

    private record FlagOutcome(boolean flagged, String reason) {
    }

    private static final class AgentStats {
        private final AtomicInteger total = new AtomicInteger();
        private final AtomicInteger denials = new AtomicInteger();
    }
}
