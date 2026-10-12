package com.aegis.insights;

import com.aegis.contracts.DecisionEvent;
import com.aegis.detection.AnomalyDetectionService;
import com.aegis.detection.FlaggedAudit;
import com.aegis.ingestion.DecisionIngestionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Spring wrapper around the pure {@link InsightsEngine}: pulls the current
 * decisions, detection flags and review verdicts, and runs the analysis.
 *
 * Read-only and downstream of the four pipeline stages — the insights
 * engine never changes a flag, an evidence record or a decision. It's a
 * separate analytics layer, so the core evidence pipeline stays exactly
 * what the reviewer signed off on.
 */
@Service
public class InsightsService {

    private final DecisionIngestionService ingestionService;
    private final AnomalyDetectionService detectionService;
    private final ReviewVerdictRepository verdictRepository;
    private final InsightsEngine engine;

    public InsightsService(DecisionIngestionService ingestionService,
                           AnomalyDetectionService detectionService,
                           ReviewVerdictRepository verdictRepository,
                           @Value("${aegis.insights.min-sample:12}") int minSample,
                           @Value("${aegis.insights.alpha:0.01}") double alpha,
                           @Value("${aegis.insights.review-minutes-per-case:15}") double reviewMinutes,
                           @Value("${aegis.insights.impact-ratio-threshold:0.8}") double impactRatio,
                           @Value("${aegis.insights.psi-threshold:0.25}") double psiThreshold,
                           @Value("${aegis.insights.low-confidence-threshold:0.6}") double lowConfidence,
                           @Value("${aegis.insights.timeline-buckets:12}") int buckets) {
        this.ingestionService = ingestionService;
        this.detectionService = detectionService;
        this.verdictRepository = verdictRepository;
        this.engine = new InsightsEngine(new InsightsEngine.Config(
                minSample, alpha, reviewMinutes, impactRatio, psiThreshold, lowConfidence, buckets));
    }

    public InsightsReport report() {
        var decisions = ingestionService.getAllDecisions();
        Set<String> flaggedIds = detectionService.getAllFlagged().stream()
                .map(FlaggedAudit::getDecisionId).collect(Collectors.toSet());
        Map<String, ReviewVerdict> verdicts = verdictRepository.findAll().stream()
                .collect(Collectors.toMap(ReviewVerdict::getDecisionId, Function.identity(), (a, b) -> a));
        return engine.analyse(decisions, flaggedIds, verdicts, Instant.now());
    }

    /** True for the outcomes a person would review: the ones adverse to the customer. */
    static boolean isReviewable(DecisionEvent d) {
        return ReasoningQualityChecker.ADVERSE.contains(d.outcome());
    }
}
