package com.aegis.ingestion;

import com.aegis.contracts.DecisionEvent;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * REST entry point for decisions: submit one, or list everything ingested
 * so far. Backs both external submission and the decision-feed UI page's
 * polling.
 */
@RestController
@RequestMapping("/api/decisions")
public class DecisionController {

    private final DecisionIngestionService ingestionService;

    public DecisionController(DecisionIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping
    public ResponseEntity<DecisionEvent> submit(@RequestBody DecisionEvent decision) {
        DecisionEvent ingested = ingestionService.ingest(decision);
        return ResponseEntity.status(HttpStatus.CREATED).body(ingested);
    }

    @GetMapping
    public List<DecisionEvent> list() {
        return ingestionService.getAllDecisions();
    }

    @ExceptionHandler(DecisionValidationException.class)
    public ResponseEntity<Map<String, String>> handleInvalid(DecisionValidationException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }
}
