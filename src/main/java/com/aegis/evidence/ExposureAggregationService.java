package com.aegis.evidence;

import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Keeps a running, per-agent exposure summary from the decision outcomes
 * {@link EvidenceMappingService} reports: how many decisions an agent has
 * made, how many produced an audit evidence record (flagged or a review
 * gap), and which exposure category shows up most for that agent.
 *
 * This is deliberately simple arithmetic, not an actuarial model, and it is
 * explicitly informational — it does not compute a premium, a coverage
 * term, or any legal/compliance determination. It exists to demonstrate the
 * "exposure visibility" feedback loop from the project's USP (surfacing
 * which agents are generating the most audit evidence, for a human
 * underwriting/compliance team to act on) — AEGIS stops at the evidence,
 * the pricing or coverage decision stays with the carrier's own systems.
 */
@Service
public class ExposureAggregationService {

    private final Map<String, AgentStats> statsByAgent = new ConcurrentHashMap<>();

    /**
     * Records the terminal outcome of one decision for exposure-visibility
     * purposes: whether it produced an evidence record (flagged, or an
     * unreviewed review-gap) or was clean (reviewed and not flagged).
     */
    public void recordDecisionOutcome(String agentId, String decisionType, boolean evidenceRecorded) {
        if (agentId == null || agentId.isBlank()) {
            return;
        }
        AgentStats stats = statsByAgent.computeIfAbsent(agentId, id -> new AgentStats());
        stats.total.incrementAndGet();
        if (evidenceRecorded) {
            stats.evidenceCount.incrementAndGet();
            String category = EvidenceMappingService.exposureCategoryFor(decisionType);
            stats.categoryCounts.computeIfAbsent(category, c -> new AtomicInteger()).incrementAndGet();
        }
    }

    /** Per-agent exposure summary, highest evidence rate first — the agents generating the most audit evidence to review. */
    public List<AgentExposureSummary> getExposureSummary() {
        return statsByAgent.entrySet().stream()
                .map(entry -> toSummary(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingDouble(AgentExposureSummary::evidenceRate).reversed())
                .toList();
    }

    private AgentExposureSummary toSummary(String agentId, AgentStats stats) {
        int total = stats.total.get();
        int evidenceCount = stats.evidenceCount.get();
        double evidenceRate = total == 0 ? 0.0 : (double) evidenceCount / total;
        String dominantCategory = stats.categoryCounts.entrySet().stream()
                .max(Comparator.comparingInt(e -> e.getValue().get()))
                .map(Map.Entry::getKey)
                .orElse("No evidence recorded yet");
        return new AgentExposureSummary(agentId, total, evidenceCount, round(evidenceRate), dominantCategory);
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static final class AgentStats {
        private final AtomicInteger total = new AtomicInteger();
        private final AtomicInteger evidenceCount = new AtomicInteger();
        private final Map<String, AtomicInteger> categoryCounts = new ConcurrentHashMap<>();
    }

    /**
     * An exposure-visibility summary for one AI agent. Local to the evidence
     * package — not a shared contract, since only this package's
     * controller/UI use it. Informational only: no premium, no coverage
     * term, no compliance certification.
     */
    public record AgentExposureSummary(
            String agentId,
            int totalDecisions,
            int evidenceCount,
            double evidenceRate,
            String dominantExposureCategory
    ) {
    }
}
