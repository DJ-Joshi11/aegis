package com.aegis.payout;

import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Keeps a running "agent error rate" per AI agent from the decision
 * outcomes {@link PayoutEngineService} reports, and derives a simple
 * premium-adjustment signal from it: the more of an agent's decisions end
 * in a payout (wrong call or an unreviewed SLA breach), the higher its
 * suggested premium multiplier.
 *
 * This is deliberately simple arithmetic, not an actuarial model — it
 * exists to demonstrate the feedback-loop concept from the project's USP
 * ("a carrier's own agent error rate sets its premium"), not to be a real
 * pricing engine.
 */
@Service
public class PricingFeedbackService {

    /** How much a 100% error rate moves the premium multiplier above 1.0. */
    private static final double ERROR_RATE_WEIGHT = 2.0;

    private final Map<String, AgentStats> statsByAgent = new ConcurrentHashMap<>();

    /**
     * Records the terminal outcome of one decision for pricing purposes:
     * whether it ended in a payout (a bad or unreviewed decision) or was
     * clean (reviewed and not flagged).
     */
    public void recordDecisionOutcome(String agentId, boolean payoutTriggered) {
        if (agentId == null || agentId.isBlank()) {
            return;
        }
        AgentStats stats = statsByAgent.computeIfAbsent(agentId, id -> new AgentStats());
        stats.total.incrementAndGet();
        if (payoutTriggered) {
            stats.payouts.incrementAndGet();
        }
    }

    /** Per-agent pricing signal, worst error rate first — the agents that should cost more to insure. */
    public List<AgentPricingSummary> getPricingSummary() {
        return statsByAgent.entrySet().stream()
                .map(entry -> toSummary(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingDouble(AgentPricingSummary::errorRate).reversed())
                .toList();
    }

    private AgentPricingSummary toSummary(String agentId, AgentStats stats) {
        int total = stats.total.get();
        int payouts = stats.payouts.get();
        double errorRate = total == 0 ? 0.0 : (double) payouts / total;
        double premiumMultiplier = round(1.0 + errorRate * ERROR_RATE_WEIGHT);
        return new AgentPricingSummary(agentId, total, payouts, round(errorRate), premiumMultiplier);
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static final class AgentStats {
        private final AtomicInteger total = new AtomicInteger();
        private final AtomicInteger payouts = new AtomicInteger();
    }

    /**
     * A pricing signal for one AI agent. Local to the payout package —
     * not a shared contract, since only this package's controller/UI use it.
     */
    public record AgentPricingSummary(
            String agentId,
            int totalDecisions,
            int payoutCount,
            double errorRate,
            double premiumMultiplier
    ) {
    }
}
