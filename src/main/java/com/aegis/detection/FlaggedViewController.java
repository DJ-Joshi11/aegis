package com.aegis.detection;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/**
 * Serves the flags & compliance page. Separate from {@link FlaggedController}
 * (a {@code @RestController} returning JSON) — same split every other
 * package uses. Route matches what {@code layout.html}'s shared nav already
 * links to: {@code /detection/flags}.
 */
@Controller
public class FlaggedViewController {

    private final AnomalyDetectionService anomalyDetectionService;

    public FlaggedViewController(AnomalyDetectionService anomalyDetectionService) {
        this.anomalyDetectionService = anomalyDetectionService;
    }

    @GetMapping("/detection/flags")
    public String flags(Model model) {
        List<FlaggedController.FlaggedView> flagged = anomalyDetectionService.getAllFlagged().stream()
                .map(FlaggedController.FlaggedView::from)
                .toList();
        model.addAttribute("flagged", flagged);
        return "detection/flags";
    }
}
