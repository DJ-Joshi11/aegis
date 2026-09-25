package com.aegis.payout;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PricingFeedbackServiceTest {

    @Test
    void agentWithNoPayoutsHasZeroErrorRateAndBaseMultiplier() {
        PricingFeedbackService service = new PricingFeedbackService();

        service.recordDecisionOutcome("agent-a", false);
        service.recordDecisionOutcome("agent-a", false);

        List<PricingFeedbackService.AgentPricingSummary> summary = service.getPricingSummary();

        assertThat(summary).hasSize(1);
        assertThat(summary.get(0).agentId()).isEqualTo("agent-a");
        assertThat(summary.get(0).totalDecisions()).isEqualTo(2);
        assertThat(summary.get(0).payoutCount()).isZero();
        assertThat(summary.get(0).errorRate()).isZero();
        assertThat(summary.get(0).premiumMultiplier()).isEqualTo(1.0);
    }

    @Test
    void agentWithPayoutsGetsAHigherErrorRateAndMultiplier() {
        PricingFeedbackService service = new PricingFeedbackService();

        service.recordDecisionOutcome("agent-b", true);
        service.recordDecisionOutcome("agent-b", false);

        List<PricingFeedbackService.AgentPricingSummary> summary = service.getPricingSummary();

        assertThat(summary.get(0).errorRate()).isEqualTo(0.5);
        assertThat(summary.get(0).premiumMultiplier()).isGreaterThan(1.0);
    }

    @Test
    void worseAgentsSortFirst() {
        PricingFeedbackService service = new PricingFeedbackService();

        service.recordDecisionOutcome("clean-agent", false);
        service.recordDecisionOutcome("bad-agent", true);

        List<PricingFeedbackService.AgentPricingSummary> summary = service.getPricingSummary();

        assertThat(summary.get(0).agentId()).isEqualTo("bad-agent");
        assertThat(summary.get(1).agentId()).isEqualTo("clean-agent");
    }
}
