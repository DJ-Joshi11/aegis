package com.aegis.payout;

import com.aegis.contracts.DecisionEvent;
import com.aegis.contracts.FlaggedDecision;
import com.aegis.contracts.PayoutResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PayoutEngineService}, isolated from Spring and H2
 * via Mockito — covers the two payout triggers (flagged, and SLA breach via
 * the scheduled sweep) plus the clean, no-payout path.
 */
@ExtendWith(MockitoExtension.class)
class PayoutEngineServiceTest {

    @Mock
    private PayoutAuditRepository repository;

    @Mock
    private PricingFeedbackService pricingFeedbackService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private PayoutEngineService service;

    @BeforeEach
    void setUp() {
        // 1-second SLA window for the sweep test below, so it doesn't need to sleep long.
        service = new PayoutEngineService(repository, pricingFeedbackService, eventPublisher, 1);
    }

    private DecisionEvent decision(String id, String agentId) {
        return new DecisionEvent(id, agentId, "CLAIMS", "DENIED",
                Instant.now(), "US-CA", "Loss occurred after policy lapse; denied.");
    }

    @Test
    void onFlaggedDecision_triggersAnImmediatePayoutWhenFlagged() {
        DecisionEvent decision = decision("dec-101", "qsr-claims-agent-02");
        FlaggedDecision flagged = new FlaggedDecision(decision, true, "denial rate outlier for this agent", "EU AI Act Art.14 explainability");

        service.onDecisionIngested(decision);
        service.onFlaggedDecision(flagged);

        ArgumentCaptor<PayoutAudit> savedCaptor = ArgumentCaptor.forClass(PayoutAudit.class);
        verify(repository, times(1)).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().getDecisionId()).isEqualTo("dec-101");
        assertThat(savedCaptor.getValue().isSlaBreached()).isFalse();
        assertThat(savedCaptor.getValue().getAgentId()).isEqualTo("qsr-claims-agent-02");

        ArgumentCaptor<PayoutResult> publishedCaptor = ArgumentCaptor.forClass(PayoutResult.class);
        verify(eventPublisher, times(1)).publishEvent(publishedCaptor.capture());
        assertThat(publishedCaptor.getValue().payoutTriggered()).isTrue();
        assertThat(publishedCaptor.getValue().slaBreached()).isFalse();

        verify(pricingFeedbackService, times(1)).recordDecisionOutcome("qsr-claims-agent-02", true);
    }

    @Test
    void onFlaggedDecision_recordsACleanOutcomeAndPaysNothingWhenNotFlagged() {
        DecisionEvent decision = decision("dec-102", "qsr-claims-agent-07");
        FlaggedDecision cleared = new FlaggedDecision(decision, false, null, "no issues found");

        service.onDecisionIngested(decision);
        service.onFlaggedDecision(cleared);

        verify(repository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
        verify(pricingFeedbackService, times(1)).recordDecisionOutcome("qsr-claims-agent-07", false);
    }

    @Test
    void sweepSlaBreaches_paysOutADecisionDetectionNeverReviewedInTime() throws InterruptedException {
        DecisionEvent decision = decision("dec-103", "qsr-underwriting-agent-01");
        service.onDecisionIngested(decision);

        // SLA window is 1 second (see setUp) — wait past it, then let the sweep run.
        Thread.sleep(1100);
        service.sweepSlaBreaches();

        ArgumentCaptor<PayoutAudit> savedCaptor = ArgumentCaptor.forClass(PayoutAudit.class);
        verify(repository, times(1)).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().getDecisionId()).isEqualTo("dec-103");
        assertThat(savedCaptor.getValue().isSlaBreached()).isTrue();
        assertThat(savedCaptor.getValue().getReason()).contains("SLA window");

        verify(pricingFeedbackService, times(1)).recordDecisionOutcome("qsr-underwriting-agent-01", true);
    }

    @Test
    void sweepSlaBreaches_leavesFreshDecisionsAlone() {
        service.onDecisionIngested(decision("dec-104", "qsr-fraud-agent-03"));

        service.sweepSlaBreaches();

        verify(repository, never()).save(any());
        verify(pricingFeedbackService, never()).recordDecisionOutcome(any(), anyBoolean());
    }

    @Test
    void getAllPayouts_delegatesToRepository() {
        when(repository.findAllByOrderByTriggeredAtDesc()).thenReturn(List.of());

        assertThat(service.getAllPayouts()).isEmpty();
        verify(repository, times(1)).findAllByOrderByTriggeredAtDesc();
    }
}
