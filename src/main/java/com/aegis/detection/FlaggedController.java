package com.aegis.detection;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * REST entry point for flagged decisions. Backs both external consumers
 * (the dashboard's summary call) and the flags page's polling.
 */
@RestController
@RequestMapping("/api/flagged")
public class FlaggedController {

    private final AnomalyDetectionService anomalyDetectionService;

    public FlaggedController(AnomalyDetectionService anomalyDetectionService) {
        this.anomalyDetectionService = anomalyDetectionService;
    }

    @GetMapping
    public List<FlaggedView> list() {
        return anomalyDetectionService.getAllFlagged().stream().map(FlaggedView::from).toList();
    }

    /** Flat, UI/JSON-friendly view of a FlaggedAudit row. */
    public record FlaggedView(
            String decisionId,
            String agentId,
            String decisionType,
            String outcome,
            String jurisdiction,
            String flagReason,
            String complianceStatus,
            Instant reviewedAt
    ) {
        static FlaggedView from(FlaggedAudit audit) {
            return new FlaggedView(
                    audit.getDecisionId(),
                    audit.getAgentId(),
                    audit.getDecisionType(),
                    audit.getOutcome(),
                    audit.getJurisdiction(),
                    audit.getFlagReason(),
                    audit.getComplianceStatus(),
                    audit.getReviewedAt()
            );
        }
    }
}
