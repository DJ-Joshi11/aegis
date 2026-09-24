# `com.aegis.detection` — owned by Person B

## Scope

Watch every `DecisionEvent`, flag the ones that look wrong, and check
flagged decisions against the rules their jurisdiction requires. This is
the project's strongest "novel" pitch point — the explainability/governance
layer sitting on top of agentic AI decisions.

## Build

1. **`AnomalyDetectionService`** — listens for `DecisionEvent` via
   `@EventListener`, compares it against a simple historical/statistical
   pattern (a threshold or moving-average check on decision rate per
   `decisionType`/`jurisdiction` is enough for now), and decides
   `flagged` + `flagReason`.
2. **`ComplianceRulesService`** — for a flagged decision, looks up what
   that jurisdiction requires (a simple table: jurisdiction → standard,
   e.g. EU → "EU AI Act Art.14 explainability", India → "IRDAI disclosure",
   US → "state AI disclosure rule") and sets `complianceStatus`.
3. Publishes `FlaggedDecision` as a Spring `ApplicationEvent` for the
   payout package.
4. **`FlaggedController`** — `GET /api/flagged`.
5. **UI page** — `src/main/resources/templates/detection/flags.html`,
   using the shared `nav`/`footer` fragments from `templates/layout.html`.
   Flag reason and compliance status should be the first things a reviewer
   sees per row.

## Files you own

- `src/main/java/com/aegis/detection/**`
- `src/main/resources/templates/detection/**`
- `src/test/java/com/aegis/detection/**`

## Do not touch

`com.aegis.contracts`, `templates/layout.html`, `static/css/aegis.css`,
`pom.xml`, or another package's files — raise it with the team instead.

## Getting a Claude session going

Use the "Person B" prompt from `docs/CLAUDE_PROMPTS.md` (or the project doc)
as the first message in your own Claude session, after cloning and checking
out `feature/detection`.
