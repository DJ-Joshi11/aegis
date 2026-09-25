package com.aegis.payout;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/**
 * Serves the payouts + pricing page. Separate from {@link PayoutController}
 * (a {@code @RestController} returning JSON) since this returns a
 * Thymeleaf view name — same split ingestion used for its own page. Route
 * matches what {@code layout.html}'s shared nav already links to:
 * {@code /payout/payouts}.
 */
@Controller
public class PayoutViewController {

    private final PayoutEngineService payoutEngineService;
    private final PricingFeedbackService pricingFeedbackService;

    public PayoutViewController(PayoutEngineService payoutEngineService,
                                 PricingFeedbackService pricingFeedbackService) {
        this.payoutEngineService = payoutEngineService;
        this.pricingFeedbackService = pricingFeedbackService;
    }

    @GetMapping("/payout/payouts")
    public String payouts(Model model) {
        List<PayoutController.PayoutView> payouts = payoutEngineService.getAllPayouts().stream()
                .map(PayoutController.PayoutView::from)
                .toList();
        model.addAttribute("payouts", payouts);
        model.addAttribute("pricing", pricingFeedbackService.getPricingSummary());
        return "payout/payouts";
    }
}
