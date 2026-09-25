package com.aegis.payout;

import com.aegis.contracts.DecisionEvent;
import com.aegis.contracts.FlaggedDecision;
import com.aegis.contracts.PayoutResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The parametric claims engine: turns a bad AI-agent decision into an
 * automatic payout, with no manual claims form.
 *
 * Two independent triggers, matching the project's "Pay" mechanic:
 * <ol>
 *   <li><b>Flagged</b> — detection reviewed the decision and flagged it
 *   ({@link #onFlaggedDecision}): payout fires immediately.</li>
 *   <li><b>SLA breach</b> — detection never got to the decision within the
 *   configured window ({@link #sweepSlaBreaches}): payout fires anyway, so
 *   a slow or missing review never leaves a bad decision unpaid.</li>
 * </ol>
 *
 * Both paths converge on {@link #triggerPayout}, which persists the audit
 * row, publishes the shared {@link PayoutResult} contract, and reports the
 * outcome to {@link PricingFeedbackService}.
 *
 * NOTE: while the detection package doesn't exist on {@code main} yet, every
 * decision will resolve via the SLA-breach path — that's expected, and lets
 * this package demo end-to-end on its own. Once detection is merged, most
 * decisions will resolve via the faster flagged/clean path instead, and the
 * SLA path becomes the safety net it's meant to be.
 */
@Service
public class PayoutEngineService {

    private static final Logger log = LoggerFactory.getLogger(PayoutEngineService.class);

    /** Flat payout amount for a decision detection actively flagged. Simple by design — see PricingFeedbackService. */
    private static final double FLAGGED_PAYOUT_AMOUNT = 250.0;

    /** Slightly higher than the flagged amount: an unreviewed decision is a worse outcome than a reviewed-and-caught one. */
    private static final double SLA_BREACH_PAYOUT_AMOUNT = 300.0;

    private final PayoutAuditRepository repository;
    private final PricingFeedbackService pricingFeedbackService;
    private final ApplicationEventPublisher eventPublisher;
    private final Duration slaWindow;

    /** decisionId -> when we first saw it, plus which agent made it. Cleared as each decision resolves. */
    private final Map<String, PendingDecision> pending = new ConcurrentHashMap<>();

    public PayoutEngineService(PayoutAuditRepository repository,
                                PricingFeedbackService pricingFeedbackService,
                                ApplicationEventPublisher eventPublisher,
                                @Value("${aegis.payout.sla-window-seconds:45}") long slaWindowSeconds) {
        this.repository = repository;
        this.pricingFeedbackService = pricingFeedbackService;
        this.eventPublisher = eventPublisher;
        this.slaWindow = Duration.ofSeconds(slaWindowSeconds);
    }

    /** Every ingested decision starts its SLA clock here. */
    @EventListener
    public void onDecisionIngested(DecisionEvent decision) {
        pending.putIfAbsent(decision.decisionId(), new PendingDecision(decision.agentId(), Instant.now()));
    }

    /** Detection resolved the decision — either it's flagged (pay now) or clean (no payout, just record it). */
    @EventListener
    public void onFlaggedDecision(FlaggedDecision flagged) {
        DecisionEvent decision = flagged.decision();
        PendingDecision pd = pending.remove(decision.decisionId());
        String agentId = pd != null ? pd.agentId() : decision.agentId();

        if (flagged.flagged()) {
            triggerPayout(decision.decisionId(), agentId, false,
                    "Flagged by detection: " + flagged.flagReason());
        } else {
            pricingFeedbackService.recordDecisionOutcome(agentId, false);
        }
    }

    /** Safety net: anything detection never got to within the SLA window gets paid out anyway. */
    @Scheduled(fixedDelayString = "${aegis.payout.sla-sweep-interval-ms:10000}")
    public void sweepSlaBreaches() {
        Instant cutoff = Instant.now().minus(slaWindow);
        pending.entrySet().removeIf(entry -> {
            if (entry.getValue().receivedAt().isAfter(cutoff)) {
                return false;
            }
            triggerPayout(entry.getKey(), entry.getValue().agentId(), true,
                    "No compliance review within the " + slaWindow.toSeconds() + "s SLA window");
            return true;
        });
    }

    /** All triggered payouts, most recent first. */
    public List<PayoutAudit> getAllPayouts() {
        return repository.findAllByOrderByTriggeredAtDesc();
    }

    private void triggerPayout(String decisionId, String agentId, boolean slaBreached, String reason) {
        double amount = slaBreached ? SLA_BREACH_PAYOUT_AMOUNT : FLAGGED_PAYOUT_AMOUNT;
        PayoutAudit audit = new PayoutAudit(decisionId, agentId, amount, slaBreached, reason, Instant.now());
        repository.save(audit);

        eventPublisher.publishEvent(new PayoutResult(decisionId, true, amount, slaBreached));
        pricingFeedbackService.recordDecisionOutcome(agentId, true);

        log.info("Payout triggered: decision={} agent={} amount={} slaBreached={} reason={}",
                decisionId, agentId, amount, slaBreached, reason);
    }

    private record PendingDecision(String agentId, Instant receivedAt) {
    }
}
