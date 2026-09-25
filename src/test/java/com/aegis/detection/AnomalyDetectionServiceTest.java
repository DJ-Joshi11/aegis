package com.aegis.detection;

import com.aegis.contracts.DecisionEvent;
import com.aegis.contracts.FlaggedDecision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link AnomalyDetectionService}, isolated from Spring and
 * H2 via Mockito. Covers a clearly-fine decision (not flagged), a
 * clearly-flagged one (fraud flag, always flagged), and the rate-based
 * denial-outlier path.
 */
@ExtendWith(MockitoExtension.class)
class AnomalyDetectionServiceTest {

    @Mock
    private FlaggedAuditRepository repository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private AnomalyDetectionService service;

    @BeforeEach
    void setUp() {
        service = new AnomalyDetectionService(new ComplianceRulesService(), repository, eventPublisher, 3, 0.5);
    }

    private DecisionEvent decision(String id, String agentId, String type, String outcome, String jurisdiction) {
        return new DecisionEvent(id, agentId, type, outcome, Instant.now(), jurisdiction, "test reasoning trail");
    }

    @Test
    void clearlyFineDecision_isNotFlagged() {
        DecisionEvent decision = decision("dec-201", "qsr-underwriting-agent-01", "UNDERWRITING", "APPROVED", "US-CA");

        service.onDecisionIngested(decision);

        verify(repository, never()).save(any());

        ArgumentCaptor<FlaggedDecision> captor = ArgumentCaptor.forClass(FlaggedDecision.class);
        verify(eventPublisher, times(1)).publishEvent(captor.capture());
        assertThat(captor.getValue().flagged()).isFalse();
        assertThat(captor.getValue().complianceStatus()).contains("Not applicable");
    }

    @Test
    void fraudFlagOutcome_isAlwaysFlagged() {
        DecisionEvent decision = decision("dec-202", "qsr-fraud-agent-03", "FRAUD_FLAG", "FLAGGED", "IN-MH");

        service.onDecisionIngested(decision);

        ArgumentCaptor<FlaggedAudit> savedCaptor = ArgumentCaptor.forClass(FlaggedAudit.class);
        verify(repository, times(1)).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().getDecisionId()).isEqualTo("dec-202");
        assertThat(savedCaptor.getValue().getComplianceStatus()).contains("IRDAI");

        ArgumentCaptor<FlaggedDecision> publishedCaptor = ArgumentCaptor.forClass(FlaggedDecision.class);
        verify(eventPublisher, times(1)).publishEvent(publishedCaptor.capture());
        assertThat(publishedCaptor.getValue().flagged()).isTrue();
        assertThat(publishedCaptor.getValue().flagReason()).contains("Fraud flag");
    }

    @Test
    void highDenialRateForAnAgent_flagsTheLatestDenial() {
        // Sample size threshold is 3, rate threshold 0.5 (see setUp).
        service.onDecisionIngested(decision("dec-203", "qsr-claims-agent-07", "CLAIMS", "DENIED", "EU-DE"));
        service.onDecisionIngested(decision("dec-204", "qsr-claims-agent-07", "CLAIMS", "APPROVED", "EU-DE"));
        service.onDecisionIngested(decision("dec-205", "qsr-claims-agent-07", "CLAIMS", "DENIED", "EU-DE"));

        ArgumentCaptor<FlaggedDecision> captor = ArgumentCaptor.forClass(FlaggedDecision.class);
        verify(eventPublisher, times(3)).publishEvent(captor.capture());

        FlaggedDecision latest = captor.getAllValues().get(2);
        assertThat(latest.flagged()).isTrue();
        assertThat(latest.flagReason()).contains("Denial rate");
        assertThat(latest.complianceStatus()).contains("EU AI Act");
    }

    @Test
    void lowDenialRateForAnAgent_isNotFlagged() {
        service.onDecisionIngested(decision("dec-206", "qsr-claims-agent-02", "CLAIMS", "APPROVED", "US-NY"));
        service.onDecisionIngested(decision("dec-207", "qsr-claims-agent-02", "CLAIMS", "APPROVED", "US-NY"));
        service.onDecisionIngested(decision("dec-208", "qsr-claims-agent-02", "CLAIMS", "DENIED", "US-NY"));

        verify(repository, never()).save(any());
    }
}
