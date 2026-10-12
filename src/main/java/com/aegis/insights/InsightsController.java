package com.aegis.insights;

import com.aegis.contracts.DecisionEvent;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * REST API for the analytics layer: the inferred insights report, and the
 * human review loop that feeds it ground truth.
 */
@RestController
public class InsightsController {

    private final InsightsService insightsService;
    private final ReviewService reviewService;

    public InsightsController(InsightsService insightsService, ReviewService reviewService) {
        this.insightsService = insightsService;
        this.reviewService = reviewService;
    }

    @GetMapping("/api/insights")
    public InsightsReport insights() {
        return insightsService.report();
    }

    @GetMapping("/api/reviews")
    public List<ReviewVerdict> reviews() {
        return reviewService.all();
    }

    @GetMapping("/api/reviews/queue")
    public List<DecisionEvent> queue(@RequestParam(defaultValue = "20") int limit) {
        return reviewService.queue(limit);
    }

    @PostMapping("/api/reviews/{decisionId}")
    public ResponseEntity<ReviewVerdict> review(@PathVariable String decisionId, @RequestBody ReviewRequest body) {
        ReviewVerdict saved = reviewService.record(decisionId,
                body == null ? null : body.verdict(),
                body == null ? null : body.reviewer(),
                body == null ? null : body.note());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleInvalid(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }
}
