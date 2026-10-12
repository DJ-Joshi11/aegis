package com.aegis.evidence;

import com.aegis.contracts.DecisionEvent;
import com.aegis.contracts.ExposureEvidenceRecord;
import com.aegis.contracts.FlaggedDecision;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link EvidenceMappingService}, isolated from Spring and H2
 * via Mockito — covers the two evidence triggers (flagged, and a review gap
 * via the scheduled sweep) plus the clean, no-evidence path.
 */
@ExtendWith(MockitoExtension.class)
class EvidenceMappingServiceTest {

    @Mock
    private EvidenceRecordRepository repository;

    @Mock
    private ExposureAggregationService exposureAggregationService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private EvidenceMappingService service;

    @BeforeEach
    void setUp() {
        // 1-second review window for the sweep test below, so it doesn't need to sleep long.
        service = new EvidenceMappingService(repository, exposureAggregationService, eventPublisher, 1);
    }

    private DecisionEvent decision(String id, String agentId) {
        return new DecisionEvent(id, agentId, "CLAIMS", "DENIED",
                Instant.now(), "US-CA", "Loss occurred after policy lapse; denied.",
                null, null, null);
    }

    @Test
    void onFlaggedDecision_recordsEvidenceImmediatelyWhenFlagged() {
        DecisionEvent decision = decision("dec-101", "qsr-claims-agent-02");
        FlaggedDecision flagged = new FlaggedDecision(decision, true, "denial rate outlier for this agent", "EU AI Act Art.14 explainability");

        service.onDecisionIngested(decision);
        service.onFlaggedDecision(flagged);

        ArgumentCaptor<EvidenceRecord> savedCaptor = ArgumentCaptor.forClass(EvidenceRecord.class);
        verify(repository, times(1)).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().getDecisionId()).isEqualTo("dec-101");
        assertThat(savedCaptor.getValue().isControlGapIdentified()).isFalse();
        assertThat(savedCaptor.getValue().getAgentId()).isEqualTo("qsr-claims-agent-02");
        assertThat(savedCaptor.getValue().getExposureCategory()).isEqualTo("Claims Handling Exposure");

        ArgumentCaptor<ExposureEvidenceRecord> publishedCaptor = ArgumentCaptor.forClass(ExposureEvidenceRecord.class);
        verify(eventPublisher, times(1)).publishEvent(publishedCaptor.capture());
        assertThat(publishedCaptor.getValue().evidenceRecorded()).isTrue();
        assertThat(publishedCaptor.getValue().controlGapIdentified()).isFalse();

        verify(exposureAggregationService, times(1)).recordDecisionOutcome("qsr-claims-agent-02", "CLAIMS", true);
    }

    @Test
    void onFlaggedDecision_recordsACleanOutcomeAndNoEvidenceWhenNotFlagged() {
        DecisionEvent decision = decision("dec-102", "qsr-claims-agent-07");
        FlaggedDecision cleared = new FlaggedDecision(decision, false, null, "no issues found");

        service.onDecisionIngested(decision);
        service.onFlaggedDecision(cleared);

        verify(repository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
        verify(exposureAggregationService, times(1)).recordDecisionOutcome("qsr-claims-agent-07", "CLAIMS", false);
    }

    @Test
    void sweepReviewGaps_recordsEvidenceForADecisionDetectionNeverReviewedInTime() throws InterruptedException {
        DecisionEvent decision = decision("dec-103", "qsr-underwriting-agent-01");
        service.onDecisionIngested(decision);

        // Review window is 1 second (see setUp) — wait past it, then let the sweep run.
        Thread.sleep(1100);
        service.sweepReviewGaps();

        ArgumentCaptor<EvidenceRecord> savedCaptor = ArgumentCaptor.forClass(EvidenceRecord.class);
        verify(repository, times(1)).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().getDecisionId()).isEqualTo("dec-103");
        assertThat(savedCaptor.getValue().isControlGapIdentified()).isTrue();
        assertThat(savedCaptor.getValue().getReason()).contains("review window");

        verify(exposureAggregationService, times(1))
                .recordDecisionOutcome(eq("qsr-underwriting-agent-01"), anyString(), eq(true));
    }

    @Test
    void sweepReviewGaps_leavesFreshDecisionsAlone() {
        service.onDecisionIngested(decision("dec-104", "qsr-fraud-agent-03"));

        service.sweepReviewGaps();

        verify(repository, never()).save(any());
        verify(exposureAggregationService, never()).recordDecisionOutcome(any(), any(), anyBoolean());
    }

    @Test
    void getAllEvidence_delegatesToRepository() {
        when(repository.findAllByOrderByRecordedAtDesc()).thenReturn(List.of());

        assertThat(service.getAllEvidence()).isEmpty();
        verify(repository, times(1)).findAllByOrderByRecordedAtDesc();
    }

    @Test
    void exposureCategoryFor_mapsKnownDecisionTypes() {
        assertThat(EvidenceMappingService.exposureCategoryFor("UNDERWRITING")).isEqualTo("Underwriting Risk Exposure");
        assertThat(EvidenceMappingService.exposureCategoryFor("CLAIMS")).isEqualTo("Claims Handling Exposure");
        assertThat(EvidenceMappingService.exposureCategoryFor("FRAUD_FLAG")).isEqualTo("Fraud & Integrity Exposure");
        assertThat(EvidenceMappingService.exposureCategoryFor("SOMETHING_ELSE")).isEqualTo("General AI Decision Exposure");
        assertThat(EvidenceMappingService.exposureCategoryFor(null)).isEqualTo("General AI Decision Exposure");
    }
}
