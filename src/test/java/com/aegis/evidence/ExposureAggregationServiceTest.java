package com.aegis.evidence;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExposureAggregationServiceTest {

    @Test
    void agentWithNoEvidenceHasZeroEvidenceRate() {
        ExposureAggregationService service = new ExposureAggregationService();

        service.recordDecisionOutcome("agent-a", "CLAIMS", false);
        service.recordDecisionOutcome("agent-a", "CLAIMS", false);

        List<ExposureAggregationService.AgentExposureSummary> summary = service.getExposureSummary();

        assertThat(summary).hasSize(1);
        assertThat(summary.get(0).agentId()).isEqualTo("agent-a");
        assertThat(summary.get(0).totalDecisions()).isEqualTo(2);
        assertThat(summary.get(0).evidenceCount()).isZero();
        assertThat(summary.get(0).evidenceRate()).isZero();
        assertThat(summary.get(0).dominantExposureCategory()).isEqualTo("No evidence recorded yet");
    }

    @Test
    void agentWithEvidenceGetsAHigherEvidenceRateAndADominantCategory() {
        ExposureAggregationService service = new ExposureAggregationService();

        service.recordDecisionOutcome("agent-b", "CLAIMS", true);
        service.recordDecisionOutcome("agent-b", "CLAIMS", false);

        List<ExposureAggregationService.AgentExposureSummary> summary = service.getExposureSummary();

        assertThat(summary.get(0).evidenceRate()).isEqualTo(0.5);
        assertThat(summary.get(0).dominantExposureCategory()).isEqualTo("Claims Handling Exposure");
    }

    @Test
    void worstAgentsSortFirst() {
        ExposureAggregationService service = new ExposureAggregationService();

        service.recordDecisionOutcome("clean-agent", "CLAIMS", false);
        service.recordDecisionOutcome("flagged-agent", "FRAUD_FLAG", true);

        List<ExposureAggregationService.AgentExposureSummary> summary = service.getExposureSummary();

        assertThat(summary.get(0).agentId()).isEqualTo("flagged-agent");
        assertThat(summary.get(1).agentId()).isEqualTo("clean-agent");
    }
}
