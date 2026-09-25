package com.aegis.ingestion;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the decision-feed page. Separate from {@link DecisionController}
 * (which is a {@code @RestController} returning JSON) since this returns a
 * Thymeleaf view name instead. Route matches what {@code layout.html}'s
 * shared nav already links to: {@code /ingestion/decision-feed}.
 */
@Controller
public class DecisionFeedViewController {

    private final DecisionIngestionService ingestionService;

    public DecisionFeedViewController(DecisionIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @GetMapping("/ingestion/decision-feed")
    public String decisionFeed(Model model) {
        model.addAttribute("decisions", ingestionService.getAllDecisions());
        return "ingestion/decision-feed";
    }
}
