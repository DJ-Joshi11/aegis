package com.aegis.detection;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the Stage 02 · Detection &amp; Compliance page. The page renders
 * client-side from {@link FlaggedController}'s {@code /api/flagged} (and
 * {@code /api/decisions} for the denial-rate monitor), so this controller
 * only returns the view. The old {@code /detection/flags} route still
 * resolves.
 */
@Controller
public class FlaggedViewController {

    @GetMapping({"/pipeline/detection", "/detection/flags"})
    public String detection() {
        return "pipeline/detection";
    }
}
