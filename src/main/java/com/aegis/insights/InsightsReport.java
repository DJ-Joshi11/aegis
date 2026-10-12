package com.aegis.insights;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Everything the insights engine infers from the decision stream, as one
 * JSON-serialisable snapshot ({@code GET /api/insights}).
 *
 * Informational only: these are statistical signals for a human compliance,
 * claims or underwriting lead. Nothing here is a premium, a coverage
 * decision, a compliance certification or a profitability forecast.
 */
public record InsightsReport(
        Instant generatedAt,
        Summary summary,
        List<AgentInsight> agents,
        List<SegmentDisparity> disparities,
        List<LineInsight> portfolio,
        List<CalibrationBin> calibration,
        ReasoningSummary reasoning,
        List<Finding> findings,
        List<Instant> timelineBuckets,
        Assumptions assumptions,
        String disclaimer
) {

    public record Summary(
            int decisionsAnalysed,
            int agentsMonitored,
            int agentsAtRisk,
            int driftingAgents,
            int reviewed,
            int overturned,
            double overturnRate,
            double overturnCiLow,
            double overturnCiHigh,
            double reviewWorkloadHours,
            double automationRate,
            double reasoningIssueRate,
            double overturnedValue,
            int highFindings
    ) {
    }

    public record AgentInsight(
            String agentId,
            String decisionType,
            int decisions,
            String adverseOutcome,
            int adverse,
            double adverseRate,
            double ciLow,
            double ciHigh,
            Double peerRate,
            Double zVsPeers,
            Double pVsPeers,
            boolean outlier,
            Drift drift,
            int reviewed,
            int overturned,
            double overturnRate,
            double overturnCiLow,
            double overturnCiHigh,
            Double overturnPVsRest,
            double reasoningIssueRate,
            double lowConfidenceAutoRate,
            Double avgConfidence,
            int riskScore,
            String riskBand,
            Map<String, Double> riskComponents,
            List<Double> timeline,
            List<Integer> timelineCounts,
            Map<String, Integer> outcomeMix
    ) {
    }

    public record Drift(
            int baselineN,
            int recentN,
            double baselineRate,
            double recentRate,
            double z,
            double p,
            double psi,
            boolean drifting,
            String direction
    ) {
    }

    /** {@code agentId} is null for a fleet-level (all agents of that type) comparison. */
    public record SegmentDisparity(
            String agentId,
            String decisionType,
            String dimension,
            String segment,
            int decisions,
            double adverseRate,
            double favourableRate,
            double impactRatio,
            double z,
            double p,
            boolean flagged
    ) {
    }

    public record LineInsight(
            String lineOfBusiness,
            int decisions,
            Map<String, Integer> byType,
            double automationRate,
            double humanTouchRate,
            double denialRate,
            double fraudFlagRate,
            int reviewed,
            int overturned,
            double overturnRate,
            double claimValue,
            double deniedValue,
            double overturnedValue,
            double fraudFlaggedValue,
            double reviewHours,
            String posture,
            List<Signal> signals
    ) {
    }

    public record Signal(String level, String text) {
    }

    public record CalibrationBin(
            String label,
            double low,
            double high,
            int reviewed,
            int overturned,
            double overturnRate,
            double avgConfidence
    ) {
    }

    public record ReasoningSummary(
            int checked,
            int withIssues,
            double issueRate,
            Map<String, Integer> issueCounts,
            List<ReasoningExample> examples
    ) {
    }

    public record ReasoningExample(String decisionId, String agentId, String outcome, List<String> issues, String reasoningTrail) {
    }

    public record Finding(
            String severity,
            String kind,
            String title,
            String detail,
            String agentId,
            String segment,
            String evidence
    ) {
    }

    public record Assumptions(
            int minSample,
            double alpha,
            double reviewMinutesPerCase,
            double impactRatioThreshold,
            double psiThreshold,
            double lowConfidenceThreshold,
            String currency
    ) {
    }
}
