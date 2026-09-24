# Per-person Claude kickoff prompts

Copy-paste the block for your slice as the **first message** in your own
Claude session, right after cloning the repo and checking out your branch
(see `CONTRIBUTING.md`). Each person runs their own session — don't share
one across teammates, it causes overlapping edits.

---

## Person A — Ingestion

```
I'm building the "ingestion" slice of AEGIS, a Spring Boot 3 / Java 21 app. AEGIS monitors AI-agent decisions inside a Guidewire-style insurance core system and auto-triggers payout/compliance action when a decision turns out bad. Single Maven module, one package per teammate, H2 database. I've already cloned the repo and I'm on branch feature/ingestion.

My package: com.aegis.ingestion. Full scope is in src/main/java/com/aegis/ingestion/README.md — read that file first.

Shared contract (already in com.aegis.contracts, do not modify):
record DecisionEvent(String decisionId, String agentId, String decisionType, String outcome, Instant timestamp, String jurisdiction, String reasoningTrail) {}

Build, in order:
1. MockDecisionGenerator — a scheduled job or POST /api/decisions endpoint creating sample AI-agent decisions shaped like what Guidewire's Qusar agentic framework would emit (underwriting/claims/fraud-flag decisions, plausible agentIds/decisionTypes/jurisdictions). We don't have real Qusar sandbox access, so this mock stands in for it.
2. DecisionIngestionService — validates an incoming DecisionEvent, saves it to an audit-log table (JPA + H2), publishes it as a Spring ApplicationEvent.
3. DecisionController — POST /api/decisions, GET /api/decisions.
4. UI page: templates/ingestion/decision-feed.html, using the shared nav/footer fragments from templates/layout.html (th:replace="~{layout :: nav}" and ~{layout :: footer}). A live table of incoming decisions.

Guardrail: only create/edit files under src/main/java/com/aegis/ingestion/**, src/main/resources/templates/ingestion/**, and my own test files. Never edit com.aegis.contracts, layout.html, aegis.css, pom.xml, or another package's files — flag it to me instead so we change it together.

Build me this package, service-first then the REST layer then the UI page, with a couple of unit tests for DecisionIngestionService.
```

---

## Person B — Detection & Compliance

```
I'm building the "detection" slice of AEGIS, a Spring Boot 3 / Java 21 app. AEGIS monitors AI-agent decisions inside a Guidewire-style insurance core system and auto-triggers payout/compliance action when a decision turns out bad. Single Maven module, one package per teammate, H2 database. I've already cloned the repo and I'm on branch feature/detection.

My package: com.aegis.detection. Full scope is in src/main/java/com/aegis/detection/README.md — read that file first.

Shared contracts (already in com.aegis.contracts, do not modify):
record DecisionEvent(String decisionId, String agentId, String decisionType, String outcome, Instant timestamp, String jurisdiction, String reasoningTrail) {}
record FlaggedDecision(DecisionEvent decision, boolean flagged, String flagReason, String complianceStatus) {}

Build, in order:
1. AnomalyDetectionService — listens for DecisionEvent via @EventListener, compares it against a simple historical/statistical pattern (a threshold or moving-average check on decision rate per decisionType/jurisdiction is enough), decides flagged + flagReason.
2. ComplianceRulesService — for a flagged decision, looks up what that jurisdiction requires (a simple table: jurisdiction -> standard, e.g. EU -> "EU AI Act Art.14 explainability", India -> "IRDAI disclosure", US -> "state AI disclosure rule") and sets complianceStatus.
3. Publishes FlaggedDecision as a Spring ApplicationEvent.
4. FlaggedController — GET /api/flagged.
5. UI page: templates/detection/flags.html, using the shared nav/footer fragments from templates/layout.html. Flag reason and compliance status should be the first things a reviewer sees per row — this is our strongest "novel" pitch point.

Guardrail: only create/edit files under src/main/java/com/aegis/detection/**, src/main/resources/templates/detection/**, and my own test files. Never edit com.aegis.contracts, layout.html, aegis.css, pom.xml, or another package's files — flag it to me instead so we change it together.

Build me this package, service-first then the REST layer then the UI page, with a couple of unit tests for AnomalyDetectionService covering a clearly-fine decision and a clearly-flagged one.
```

---

## Person C — Payout, Pricing & Dashboard

```
I'm building the "payout" slice of AEGIS, a Spring Boot 3 / Java 21 app. AEGIS monitors AI-agent decisions inside a Guidewire-style insurance core system and auto-triggers payout/compliance action when a decision turns out bad. Single Maven module, one package per teammate, H2 database. I've already cloned the repo and I'm on branch feature/payout.

My package: com.aegis.payout. Full scope is in src/main/java/com/aegis/payout/README.md — read that file first.

Shared contracts (already in com.aegis.contracts, do not modify):
record DecisionEvent(String decisionId, String agentId, String decisionType, String outcome, Instant timestamp, String jurisdiction, String reasoningTrail) {}
record FlaggedDecision(DecisionEvent decision, boolean flagged, String flagReason, String complianceStatus) {}
record PayoutResult(String decisionId, boolean payoutTriggered, double amount, boolean slaBreached) {}

Build, in order:
1. PayoutEngineService — listens for FlaggedDecision via @EventListener; if flagged is true (or an SLA window, e.g. 48h, is breached with no resolution), computes a payout amount with a simple parametric rule, publishes a PayoutResult.
2. PricingFeedbackService — keeps a running "agent error rate" per agentId from PayoutResults seen so far, and a simple premium-adjustment number derived from it. Simple math is fine.
3. PayoutController + DashboardController — GET /api/payouts, and the "/" route (replace the day-0 com.aegis.HomeController placeholder once this exists — delete that class). Pull a summary across all three slices for the dashboard (call the other REST endpoints, or read shared H2 tables directly — whichever is simpler given the timeline).
4. UI pages: templates/payout/payouts.html (payout list + pricing signal) and templates/dashboard.html (the landing page — recent decisions, flagged count, payouts triggered, current premium signal; make decision -> flag -> payout -> price visually obvious). Both use the shared nav/footer fragments from templates/layout.html.

Guardrail: only create/edit files under src/main/java/com/aegis/payout/**, src/main/resources/templates/payout/**, templates/dashboard.html, com/aegis/HomeController.java, and my own test files. Never edit com.aegis.contracts, layout.html, aegis.css, pom.xml, or another package's own files — flag it to me instead so we change it together.

Build me this package, service-first then the REST layer then the UI pages, with a couple of unit tests for PayoutEngineService covering an SLA-breach case and a clean case.
```
