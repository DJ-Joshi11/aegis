package com.aegis.evidence;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the Stage 03 · Evidence Mapping and Stage 04 · Exposure
 * Aggregation pages. Both render client-side from {@link EvidenceController}'s
 * JSON endpoints ({@code /api/evidence}, {@code /api/exposure-summary},
 * {@code /api/evidence/export}) and poll for live updates, so these handlers
 * only return the views. The old {@code /evidence/records} route still
 * resolves.
 */
@Controller
public class EvidenceViewController {

    @GetMapping({"/pipeline/evidence", "/evidence/records"})
    public String evidence() {
        return "pipeline/evidence";
    }

    @GetMapping("/pipeline/exposure")
    public String exposure() {
        return "pipeline/exposure";
    }
}
