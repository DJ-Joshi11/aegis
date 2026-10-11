package com.aegis.evidence;

import com.aegis.contracts.DecisionEvent;
import com.aegis.contracts.ExposureEvidenceRecord;
import com.aegis.contracts.FlaggedDecision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The exposure-evidence mapper: turns a flagged (or never-reviewed) AI-agent
 * decision into an audit evidence record — which broad insurance exposure
 * category the decision falls under, and whether a control gap was
 * identified. No money moves here and no coverage or legal determination is
 * made; this is evidence for a human compliance or underwriting reviewer.
 *
 * Two independent triggers, matching the project's "Watch → evidence" chain:
 * <ol>
 *   <li><b>Flagged</b> — detection reviewed the decision and flagged it
 *   ({@link #onFlaggedDecision}): an evidence record is created immediately,
 *   tagged with the exposure category the decision type maps to.</li>
 *   <li><b>Review gap</b> — detection never got to the decision within the
 *   configured review window ({@link #sweepReviewGaps}): an evidence record
 *   is still created, flagged as a control gap, so a slow or missing review
 *   never leaves a decision with no audit trail.</li>
 * </ol>
 *
 * Both paths converge on {@link #recordEvidence}, which persists the audit
 * row, publishes the shared {@link ExposureEvidenceRecord} contract, and
 * reports the outcome to {@link ExposureAggregationService} for the
 * (non-binding, informational) exposure summary.
 *
 * NOTE: while the detection package doesn't exist on {@code main} yet, every
 * decision will resolve via the review-gap path — that's expected, and lets
 * this package demo end-to-end on its own. Once detection is merged, most
 * decisions will resolve via the faster flagged/clean path instead, and the
 * review-gap sweep becomes the safety net it's meant to be.
 */
@Service
public class EvidenceMappingService {

    private static final Logger log = LoggerFactory.getLogger(EvidenceMappingService.class);

    private final EvidenceRecordRepository repository;
    private final ExposureAggregationService exposureAggregationService;
    private final ApplicationEventPublisher eventPublisher;
    private final Duration reviewWindow;

    /** decisionId -> when we first saw it, plus which agent/decision type made it. Cleared as each decision resolves. */
    private final Map<String, PendingDecision> pending = new ConcurrentHashMap<>();

    public EvidenceMappingService(EvidenceRecordRepository repository,
                                   ExposureAggregationService exposureAggregationService,
                                   ApplicationEventPublisher eventPublisher,
                                   @Value("${aegis.evidence.review-window-seconds:45}") long reviewWindowSeconds) {
        this.repository = repository;
        this.exposureAggregationService = exposureAggregationService;
        this.eventPublisher = eventPublisher;
        this.reviewWindow = Duration.ofSeconds(reviewWindowSeconds);
    }

    /**
     * Every ingested decision starts its review-window clock here.
     *
     * {@code @Order(HIGHEST_PRECEDENCE)} matters: {@link DecisionEvent} also
     * has a listener in the detection package
     * ({@code AnomalyDetectionService.onDecisionIngested}), which publishes
     * {@link FlaggedDecision} synchronously as part of handling the same
     * event. Spring doesn't guarantee an order between listeners in
     * different beans, so without this, detection can sometimes resolve
     * (and this class's {@link #onFlaggedDecision} can run) *before* this
     * method has registered the decision as pending — at which point this
     * method adds it back to {@code pending} right after, and it never gets
     * removed again, so the review-gap sweep records it a second time even
     * though it was already reviewed and clean. Forcing this listener to
     * run first guarantees the decision is always pending before detection
     * can resolve it.
     */
    @EventListener
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public void onDecisionIngested(DecisionEvent decision) {
        pending.putIfAbsent(decision.decisionId(),
                new PendingDecision(decision.agentId(), decision.decisionType(), Instant.now()));
    }

    /** Detection resolved the decision — either it's flagged (record evidence now) or clean (no evidence record needed). */
    @EventListener
    public void onFlaggedDecision(FlaggedDecision flagged) {
        DecisionEvent decision = flagged.decision();
        PendingDecision pd = pending.remove(decision.decisionId());
        String agentId = pd != null ? pd.agentId() : decision.agentId();
        String decisionType = pd != null ? pd.decisionType() : decision.decisionType();

        if (flagged.flagged()) {
            recordEvidence(decision.decisionId(), agentId, decisionType, false,
                    "Flagged by detection: " + flagged.flagReason());
        } else {
            exposureAggregationService.recordDecisionOutcome(agentId, decisionType, false);
        }
    }

    /** Safety net: anything detection never got to within the review window still gets an evidence record. */
    @Scheduled(fixedDelayString = "${aegis.evidence.gap-sweep-interval-ms:10000}")
    public void sweepReviewGaps() {
        Instant cutoff = Instant.now().minus(reviewWindow);
        pending.entrySet().removeIf(entry -> {
            if (entry.getValue().receivedAt().isAfter(cutoff)) {
                return false;
            }
            PendingDecision pd = entry.getValue();
            recordEvidence(entry.getKey(), pd.agentId(), pd.decisionType(), true,
                    "No compliance review within the " + reviewWindow.toSeconds() + "s review window");
            return true;
        });
    }

    /** All recorded evidence, most recent first. */
    public List<EvidenceRecord> getAllEvidence() {
        return repository.findAllByOrderByRecordedAtDesc();
    }

    /** Maps a Qusar-style decision type to the broad insurance exposure category it falls under. Categorization only — never a coverage or legal determination. */
    static String exposureCategoryFor(String decisionType) {
        if (decisionType == null) {
            return "General AI Decision Exposure";
        }
        return switch (decisionType) {
            case "UNDERWRITING" -> "Underwriting Risk Exposure";
            case "CLAIMS" -> "Claims Handling Exposure";
            case "FRAUD_FLAG" -> "Fraud & Integrity Exposure";
            default -> "General AI Decision Exposure";
        };
    }

    private void recordEvidence(String decisionId, String agentId, String decisionType, boolean controlGapIdentified, String reason) {
        String exposureCategory = exposureCategoryFor(decisionType);
        EvidenceRecord record = new EvidenceRecord(decisionId, agentId, exposureCategory, controlGapIdentified, reason, Instant.now());
        repository.save(record);

        eventPublisher.publishEvent(new ExposureEvidenceRecord(decisionId, true, exposureCategory, controlGapIdentified));
        exposureAggregationService.recordDecisionOutcome(agentId, decisionType, true);

        log.info("Evidence recorded: decision={} agent={} exposureCategory={} controlGapIdentified={} reason={}",
                decisionId, agentId, exposureCategory, controlGapIdentified, reason);
    }

    private record PendingDecision(String agentId, String decisionType, Instant receivedAt) {
    }
}
