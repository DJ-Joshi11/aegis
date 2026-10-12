package com.aegis.insights;

import com.aegis.contracts.DecisionEvent;
import com.aegis.ingestion.DecisionAuditRepository;
import com.aegis.ingestion.DecisionIngestionService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Records a human reviewer's verdict on an AI decision and serves the
 * queue of adverse decisions still waiting for one. AEGIS stores the
 * person's call — it never makes it.
 */
@Service
public class ReviewService {

    private final ReviewVerdictRepository repository;
    private final DecisionIngestionService ingestionService;
    private final DecisionAuditRepository decisionRepository;

    public ReviewService(ReviewVerdictRepository repository, DecisionIngestionService ingestionService,
                         DecisionAuditRepository decisionRepository) {
        this.repository = repository;
        this.ingestionService = ingestionService;
        this.decisionRepository = decisionRepository;
    }

    public ReviewVerdict record(String decisionId, String verdict, String reviewer, String note) {
        String v = verdict == null ? "" : verdict.trim().toUpperCase(Locale.ROOT);
        if (!ReviewVerdict.UPHELD.equals(v) && !ReviewVerdict.OVERTURNED.equals(v)) {
            throw new IllegalArgumentException("verdict must be UPHELD or OVERTURNED");
        }
        if (decisionId == null || !decisionRepository.existsById(decisionId)) {
            throw new IllegalArgumentException("decision '" + decisionId + "' has not been ingested");
        }
        if (repository.existsById(decisionId)) {
            throw new IllegalArgumentException("decision '" + decisionId + "' already has a review verdict");
        }
        String who = reviewer == null || reviewer.isBlank() ? "console-reviewer" : reviewer.trim();
        return repository.save(new ReviewVerdict(decisionId, v, who, note == null ? null : note.trim(), Instant.now()));
    }

    public List<ReviewVerdict> all() {
        return repository.findAllByOrderByReviewedAtDesc();
    }

    public Optional<ReviewVerdict> find(String decisionId) {
        return repository.findById(decisionId);
    }

    /** Adverse decisions with no verdict yet, newest first. */
    public List<DecisionEvent> queue(int limit) {
        Map<String, ReviewVerdict> verdicts = repository.findAll().stream()
                .collect(Collectors.toMap(ReviewVerdict::getDecisionId, Function.identity(), (a, b) -> a));
        return ingestionService.getAllDecisions().stream()
                .filter(InsightsService::isReviewable)
                .filter(d -> !verdicts.containsKey(d.decisionId()))
                .limit(Math.max(1, Math.min(limit, 200)))
                .toList();
    }
}
