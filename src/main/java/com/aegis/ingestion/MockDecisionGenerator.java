package com.aegis.ingestion;

import com.aegis.contracts.DecisionEvent;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Stands in for a real Guidewire Qusar agent feed, which we don't have
 * sandbox access to (see the main README's "How we're using Qusar"
 * section). On a fixed schedule, generates one plausible AI-agent decision
 * — underwriting, claims, or fraud-flag — and runs it through the same
 * {@link DecisionIngestionService} a real feed would use, so the rest of
 * the pipeline (audit log, detection, payout) can't tell the difference.
 *
 * The interval is overridable via {@code aegis.ingestion.mock-interval-ms}
 * (defaults to 8000ms) without needing to touch the shared
 * {@code application.properties}.
 */
@Component
public class MockDecisionGenerator {

    private final DecisionIngestionService ingestionService;
    private final Random random = new Random();

    public MockDecisionGenerator(DecisionIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    private record DecisionTemplate(
            String decisionType,
            List<String> agentIds,
            List<String> outcomes,
            List<String> reasoningTrails
    ) {
    }

    private static final List<String> JURISDICTIONS =
            List.of("US-CA", "US-NY", "US-TX", "EU-DE", "EU-FR", "IN-MH", "UK-LON");

    private static final List<DecisionTemplate> TEMPLATES = List.of(
            new DecisionTemplate(
                    "UNDERWRITING",
                    List.of("qsr-underwriting-agent-01", "qsr-underwriting-agent-04"),
                    List.of("APPROVED", "DENIED", "REFERRED_TO_HUMAN"),
                    List.of(
                            "Applicant risk score 0.62, within carrier appetite band; auto-approved per policy UW-108.",
                            "Credit score 611 below 650 threshold; risk score 0.81 exceeds appetite; auto-denied per policy UW-114.",
                            "Property in flood zone AE with no prior claims data; confidence below auto-decision threshold, referred to human underwriter."
                    )
            ),
            new DecisionTemplate(
                    "CLAIMS",
                    List.of("qsr-claims-agent-02", "qsr-claims-agent-07"),
                    List.of("APPROVED", "DENIED", "PARTIAL_APPROVAL"),
                    List.of(
                            "Damage estimate $4,200 consistent with submitted photos and adjuster notes; approved in full.",
                            "Loss occurred 11 days after policy lapse; no coverage in force at time of loss; claim denied.",
                            "Estimate exceeds policy sublimit for water damage; approved up to sublimit of $10,000, balance denied."
                    )
            ),
            new DecisionTemplate(
                    "FRAUD_FLAG",
                    List.of("qsr-fraud-agent-03"),
                    List.of("FLAGGED", "CLEARED"),
                    List.of(
                            "Claim filed 2 days after policy inception; similar loss pattern to 3 prior claims by same claimant; flagged for SIU review.",
                            "Claimant history and loss circumstances consistent with baseline; no fraud indicators found, cleared."
                    )
            )
    );

    @Scheduled(fixedRateString = "${aegis.ingestion.mock-interval-ms:8000}")
    public void generateDecision() {
        DecisionTemplate template = pickRandom(TEMPLATES);
        int i = random.nextInt(template.outcomes().size());

        DecisionEvent mockEvent = new DecisionEvent(
                UUID.randomUUID().toString(),
                pickRandom(template.agentIds()),
                template.decisionType(),
                template.outcomes().get(i),
                Instant.now(),
                pickRandom(JURISDICTIONS),
                template.reasoningTrails().get(i)
        );

        ingestionService.ingest(mockEvent);
    }

    private <T> T pickRandom(List<T> options) {
        return options.get(random.nextInt(options.size()));
    }
}
