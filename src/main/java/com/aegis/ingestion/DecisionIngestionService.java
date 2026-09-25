package com.aegis.ingestion;

import com.aegis.contracts.DecisionEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Validates an incoming {@link DecisionEvent}, persists it to the audit-log
 * table, and publishes it as a Spring application event for the detection
 * package (and anyone else) to react to.
 *
 * Entry point for every decision AEGIS sees, whether it came from
 * {@link MockDecisionGenerator}'s scheduled job or {@link DecisionController}'s
 * {@code POST /api/decisions}.
 */
@Service
public class DecisionIngestionService {

    private final DecisionAuditRepository repository;
    private final ApplicationEventPublisher eventPublisher;

    public DecisionIngestionService(DecisionAuditRepository repository,
                                     ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Validates, normalizes (fills in a generated {@code decisionId} /
     * {@code timestamp} if the caller left them blank), persists, and
     * publishes the given decision.
     *
     * @return the normalized decision as it was actually stored
     * @throws DecisionValidationException if a required field is blank or
     *                                      the decisionId has already been ingested
     */
    public DecisionEvent ingest(DecisionEvent incoming) {
        DecisionEvent normalized = normalize(incoming);
        validate(normalized);

        if (repository.existsById(normalized.decisionId())) {
            throw new DecisionValidationException(
                    "decision '" + normalized.decisionId() + "' has already been ingested");
        }

        repository.save(toEntity(normalized));
        eventPublisher.publishEvent(normalized);
        return normalized;
    }

    /** All ingested decisions, most recent first. */
    public List<DecisionEvent> getAllDecisions() {
        return repository.findAllByOrderByTimestampDesc().stream()
                .map(DecisionIngestionService::toEvent)
                .toList();
    }

    private DecisionEvent normalize(DecisionEvent incoming) {
        if (incoming == null) {
            throw new DecisionValidationException("decision payload is required");
        }
        String decisionId = isBlank(incoming.decisionId())
                ? UUID.randomUUID().toString()
                : incoming.decisionId().trim();
        Instant timestamp = incoming.timestamp() == null ? Instant.now() : incoming.timestamp();

        return new DecisionEvent(
                decisionId,
                trimOrNull(incoming.agentId()),
                trimOrNull(incoming.decisionType()),
                trimOrNull(incoming.outcome()),
                timestamp,
                trimOrNull(incoming.jurisdiction()),
                trimOrNull(incoming.reasoningTrail())
        );
    }

    private void validate(DecisionEvent event) {
        requireNonBlank(event.agentId(), "agentId");
        requireNonBlank(event.decisionType(), "decisionType");
        requireNonBlank(event.outcome(), "outcome");
        requireNonBlank(event.jurisdiction(), "jurisdiction");
        requireNonBlank(event.reasoningTrail(), "reasoningTrail");
    }

    private void requireNonBlank(String value, String fieldName) {
        if (isBlank(value)) {
            throw new DecisionValidationException(fieldName + " is required");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String trimOrNull(String value) {
        return value == null ? null : value.trim();
    }

    private static DecisionAudit toEntity(DecisionEvent event) {
        return new DecisionAudit(
                event.decisionId(),
                event.agentId(),
                event.decisionType(),
                event.outcome(),
                event.timestamp(),
                event.jurisdiction(),
                event.reasoningTrail(),
                Instant.now()
        );
    }

    private static DecisionEvent toEvent(DecisionAudit audit) {
        return new DecisionEvent(
                audit.getDecisionId(),
                audit.getAgentId(),
                audit.getDecisionType(),
                audit.getOutcome(),
                audit.getTimestamp(),
                audit.getJurisdiction(),
                audit.getReasoningTrail()
        );
    }
}
