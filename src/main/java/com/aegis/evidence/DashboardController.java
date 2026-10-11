package com.aegis.evidence;

import com.aegis.contracts.DecisionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

/**
 * The landing page: the whole pipeline at a glance — recent decisions, how
 * many have been flagged, how much audit evidence has been recorded, and
 * the current exposure summary. Takes over "/" from the day-0
 * {@code com.aegis.HomeController} placeholder, which this package deletes.
 *
 * Pulls ingestion and detection data over their own REST APIs (rather than
 * importing their internal classes) so this package stays independent of
 * how those packages are implemented internally — exactly the boundary the
 * team's vertical-slice split is meant to protect. Both calls degrade
 * gracefully: ingestion is expected to always be up (merged on day 1), but
 * detection may not be merged yet, so a missing {@code /api/flagged} shows
 * as "not available yet" instead of breaking the page.
 */
@Controller
public class DashboardController {

    private static final Logger log = LoggerFactory.getLogger(DashboardController.class);

    private final RestClient restClient;
    private final EvidenceMappingService evidenceMappingService;
    private final ExposureAggregationService exposureAggregationService;

    public DashboardController(@Value("${server.port:8080}") int serverPort,
                                EvidenceMappingService evidenceMappingService,
                                ExposureAggregationService exposureAggregationService) {
        this.restClient = RestClient.create("http://localhost:" + serverPort);
        this.evidenceMappingService = evidenceMappingService;
        this.exposureAggregationService = exposureAggregationService;
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        List<DecisionEvent> recentDecisions = fetchDecisions();
        Integer flaggedCount = fetchFlaggedCount();

        model.addAttribute("recentDecisions", recentDecisions.stream().limit(10).toList());
        model.addAttribute("decisionCount", recentDecisions.size());
        model.addAttribute("flaggedCount", flaggedCount);
        model.addAttribute("detectionAvailable", flaggedCount != null);
        model.addAttribute("evidence", evidenceMappingService.getAllEvidence().stream().limit(10).toList());
        model.addAttribute("evidenceCount", evidenceMappingService.getAllEvidence().size());
        model.addAttribute("exposureSummary", exposureAggregationService.getExposureSummary());
        return "dashboard";
    }

    private List<DecisionEvent> fetchDecisions() {
        try {
            DecisionEvent[] decisions = restClient.get()
                    .uri("/api/decisions")
                    .retrieve()
                    .body(DecisionEvent[].class);
            return decisions == null ? List.of() : List.of(decisions);
        } catch (RestClientException ex) {
            log.warn("Could not reach ingestion's /api/decisions: {}", ex.getMessage());
            return List.of();
        }
    }

    /** Null means "detection isn't available yet" (not merged, or the call failed) — distinct from a real zero. */
    private Integer fetchFlaggedCount() {
        try {
            Object[] flagged = restClient.get()
                    .uri("/api/flagged")
                    .retrieve()
                    .body(Object[].class);
            return flagged == null ? 0 : flagged.length;
        } catch (RestClientException ex) {
            log.info("Detection's /api/flagged isn't available yet ({}) — showing as not-yet-available on the dashboard.",
                    ex.getMessage());
            return null;
        }
    }
}
