package com.aegis.ingestion;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the Stage 01 · Ingestion page. The page is a client-side console:
 * it renders from {@link DecisionController}'s {@code /api/decisions} (plus
 * the other stages' APIs for each decision's pipeline trace) and polls for
 * live updates, so this controller only needs to return the view.
 *
 * The old {@code /ingestion/decision-feed} route still resolves, so any
 * existing links keep working.
 */
@Controller
public class DecisionFeedViewController {

    @GetMapping({"/pipeline/ingestion", "/ingestion/decision-feed"})
    public String ingestion() {
        return "pipeline/ingestion";
    }
}
