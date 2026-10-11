package com.aegis.evidence;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * REST entry point for audit evidence and the exposure summary derived from
 * it. Backs both external consumers and the evidence page's polling.
 */
@RestController
@RequestMapping("/api")
public class EvidenceController {

    private final EvidenceMappingService evidenceMappingService;
    private final ExposureAggregationService exposureAggregationService;

    public EvidenceController(EvidenceMappingService evidenceMappingService,
                               ExposureAggregationService exposureAggregationService) {
        this.evidenceMappingService = evidenceMappingService;
        this.exposureAggregationService = exposureAggregationService;
    }

    @GetMapping("/evidence")
    public List<EvidenceView> evidence() {
        return evidenceMappingService.getAllEvidence().stream().map(EvidenceView::from).toList();
    }

    @GetMapping("/exposure-summary")
    public List<ExposureAggregationService.AgentExposureSummary> exposureSummary() {
        return exposureAggregationService.getExposureSummary();
    }

    /**
     * Downloadable audit evidence bundle — every recorded evidence row plus
     * the exposure summary it rolled up into, as one JSON file a compliance
     * reviewer can save and attach to a review. This, not a payout or a
     * pricing decision, is AEGIS's actual deliverable per the reviewer
     * scope: "consume evidence ... export audit evidence."
     */
    @GetMapping("/evidence/export")
    public ResponseEntity<Map<String, Object>> exportEvidence() {
        Map<String, Object> bundle = Map.of(
                "exportedAt", Instant.now().toString(),
                "disclaimer", "AEGIS audit evidence export. Informational only — does not certify compliance, "
                        + "make a legal coverage determination, or set a premium.",
                "evidenceRecords", evidenceMappingService.getAllEvidence().stream().map(EvidenceView::from).toList(),
                "exposureSummaryByAgent", exposureAggregationService.getExposureSummary()
        );
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"aegis-audit-evidence.json\"")
                .body(bundle);
    }

    /** Flat, UI/JSON-friendly view of an EvidenceRecord row. */
    public record EvidenceView(
            String decisionId,
            String agentId,
            String exposureCategory,
            boolean controlGapIdentified,
            String reason,
            Instant recordedAt
    ) {
        static EvidenceView from(EvidenceRecord record) {
            return new EvidenceView(
                    record.getDecisionId(),
                    record.getAgentId(),
                    record.getExposureCategory(),
                    record.isControlGapIdentified(),
                    record.getReason(),
                    record.getRecordedAt()
            );
        }
    }
}
