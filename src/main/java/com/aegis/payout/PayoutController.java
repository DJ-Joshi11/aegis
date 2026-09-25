package com.aegis.payout;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * REST entry point for payouts and the pricing signal derived from them.
 * Backs both external consumers and the payouts page's polling.
 */
@RestController
@RequestMapping("/api")
public class PayoutController {

    private final PayoutEngineService payoutEngineService;
    private final PricingFeedbackService pricingFeedbackService;

    public PayoutController(PayoutEngineService payoutEngineService,
                             PricingFeedbackService pricingFeedbackService) {
        this.payoutEngineService = payoutEngineService;
        this.pricingFeedbackService = pricingFeedbackService;
    }

    @GetMapping("/payouts")
    public List<PayoutView> payouts() {
        return payoutEngineService.getAllPayouts().stream().map(PayoutView::from).toList();
    }

    @GetMapping("/pricing")
    public List<PricingFeedbackService.AgentPricingSummary> pricing() {
        return pricingFeedbackService.getPricingSummary();
    }

    /** Flat, UI/JSON-friendly view of a PayoutAudit row. */
    public record PayoutView(
            String decisionId,
            String agentId,
            double amount,
            boolean slaBreached,
            String reason,
            Instant triggeredAt
    ) {
        static PayoutView from(PayoutAudit audit) {
            return new PayoutView(
                    audit.getDecisionId(),
                    audit.getAgentId(),
                    audit.getAmount(),
                    audit.isSlaBreached(),
                    audit.getReason(),
                    audit.getTriggeredAt()
            );
        }
    }
}
