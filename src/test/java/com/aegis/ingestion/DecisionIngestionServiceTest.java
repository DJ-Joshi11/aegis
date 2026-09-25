package com.aegis.ingestion;

import com.aegis.contracts.DecisionEvent;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DecisionIngestionService}, isolated from Spring and
 * H2 via Mockito — fast, and enough to cover the validation/persist/publish
 * contract the rest of the pipeline depends on.
 */
@ExtendWith(MockitoExtension.class)
class DecisionIngestionServiceTest {

    @Mock
    private DecisionAuditRepository repository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private DecisionIngestionService service;

    @BeforeEach
    void setUp() {
        service = new DecisionIngestionService(repository, eventPublisher);
    }

    private DecisionEvent validDecision() {
        return new DecisionEvent(
                "dec-001",
                "qsr-underwriting-agent-01",
                "UNDERWRITING",
                "APPROVED",
                Instant.parse("2026-09-25T10:00:00Z"),
                "US-CA",
                "Risk score 0.42, within appetite; auto-approved."
        );
    }

    @Test
    void ingest_savesAndPublishesAValidDecision() {
        DecisionEvent decision = validDecision();
        when(repository.existsById("dec-001")).thenReturn(false);

        DecisionEvent result = service.ingest(decision);

        assertThat(result).isEqualTo(decision);

        ArgumentCaptor<DecisionAudit> savedCaptor = ArgumentCaptor.forClass(DecisionAudit.class);
        verify(repository, times(1)).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().getDecisionId()).isEqualTo("dec-001");
        assertThat(savedCaptor.getValue().getAgentId()).isEqualTo("qsr-underwriting-agent-01");

        ArgumentCaptor<DecisionEvent> publishedCaptor = ArgumentCaptor.forClass(DecisionEvent.class);
        verify(eventPublisher, times(1)).publishEvent(publishedCaptor.capture());
        assertThat(publishedCaptor.getValue()).isEqualTo(decision);
    }

    @Test
    void ingest_generatesDecisionIdAndTimestampWhenMissing() {
        DecisionEvent decision = new DecisionEvent(
                null, "qsr-claims-agent-02", "CLAIMS", "APPROVED",
                null, "EU-DE", "Damage estimate consistent with photos; approved."
        );
        when(repository.existsById(any())).thenReturn(false);

        DecisionEvent result = service.ingest(decision);

        assertThat(result.decisionId()).isNotBlank();
        assertThat(result.timestamp()).isNotNull();
    }

    @Test
    void ingest_rejectsDecisionMissingRequiredField() {
        DecisionEvent missingAgentId = new DecisionEvent(
                "dec-002", " ", "CLAIMS", "APPROVED",
                Instant.now(), "US-NY", "Some reasoning."
        );

        assertThatThrownBy(() -> service.ingest(missingAgentId))
                .isInstanceOf(DecisionValidationException.class)
                .hasMessageContaining("agentId");

        verify(repository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void ingest_rejectsDuplicateDecisionId() {
        DecisionEvent decision = validDecision();
        when(repository.existsById("dec-001")).thenReturn(true);

        assertThatThrownBy(() -> service.ingest(decision))
                .isInstanceOf(DecisionValidationException.class)
                .hasMessageContaining("dec-001");

        verify(repository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void getAllDecisions_mapsAuditRowsBackToDecisionEvents() {
        DecisionAudit audit = new DecisionAudit(
                "dec-003", "qsr-fraud-agent-03", "FRAUD_FLAG", "FLAGGED",
                Instant.parse("2026-09-25T09:00:00Z"), "IN-MH",
                "Claim filed 2 days after policy inception; flagged for SIU review.",
                Instant.now()
        );
        when(repository.findAllByOrderByTimestampDesc()).thenReturn(List.of(audit));

        List<DecisionEvent> results = service.getAllDecisions();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).decisionId()).isEqualTo("dec-003");
        assertThat(results.get(0).jurisdiction()).isEqualTo("IN-MH");
    }
}
