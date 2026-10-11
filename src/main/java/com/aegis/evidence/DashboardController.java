package com.aegis.evidence;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * The landing page: the interactive pipeline overview (Guidewire → Qusar →
 * the four AEGIS stages → outputs).
 *
 * The page is a client-side console that reads every stage's own REST API
 * directly ({@code /api/decisions}, {@code /api/flagged}, {@code /api/evidence},
 * {@code /api/exposure-summary}) and polls for live updates, so this package
 * still never imports another slice's internal classes — and the controller
 * no longer needs to make server-side HTTP calls back into its own app.
 */
@Controller
public class DashboardController {

    @GetMapping("/")
    public String dashboard() {
        return "dashboard";
    }
}
