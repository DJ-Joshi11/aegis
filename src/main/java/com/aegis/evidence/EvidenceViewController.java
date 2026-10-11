package com.aegis.evidence;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/**
 * Serves the audit evidence + exposure summary page. Separate from
 * {@link EvidenceController} (a {@code @RestController} returning JSON)
 * since this returns a Thymeleaf view name — same split ingestion used for
 * its own page. Route matches what {@code layout.html}'s shared nav already
 * links to: {@code /evidence/records}.
 */
@Controller
public class EvidenceViewController {

    private final EvidenceMappingService evidenceMappingService;
    private final ExposureAggregationService exposureAggregationService;

    public EvidenceViewController(EvidenceMappingService evidenceMappingService,
                                   ExposureAggregationService exposureAggregationService) {
        this.evidenceMappingService = evidenceMappingService;
        this.exposureAggregationService = exposureAggregationService;
    }

    @GetMapping("/evidence/records")
    public String records(Model model) {
        List<EvidenceController.EvidenceView> evidence = evidenceMappingService.getAllEvidence().stream()
                .map(EvidenceController.EvidenceView::from)
                .toList();
        model.addAttribute("evidence", evidence);
        model.addAttribute("exposureSummary", exposureAggregationService.getExposureSummary());
        return "evidence/records";
    }
}
