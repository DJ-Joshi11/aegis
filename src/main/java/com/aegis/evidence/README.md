# `com.aegis.evidence` — owned by Person C

## Scope

Turn a flagged (or never-reviewed) decision into an audit evidence record —
mapped to a broad insurance exposure category — keep a running,
informational exposure summary per agent, and serve the main dashboard —
the single page reviewers will look at first.

**Explicitly out of scope, per the course review feedback on this project:**
AEGIS does not certify compliance, does not make a legal coverage
determination, and does not set a premium. Those stay with Guidewire's own
systems and the carrier's own compliance/underwriting teams — this package
only produces evidence and a non-binding exposure signal for a human to act
on.

## Build

1. **`EvidenceMappingService`** — listens for `FlaggedDecision` via
   `@EventListener`; if `flagged` is true (or a review window, e.g. 48h,
   passes with no resolution), maps the decision's type to an exposure
   category (`UNDERWRITING` → Underwriting Risk Exposure, `CLAIMS` → Claims
   Handling Exposure, `FRAUD_FLAG` → Fraud & Integrity Exposure) and
   publishes an `ExposureEvidenceRecord`.
2. **`ExposureAggregationService`** — keeps a running "evidence rate" and
   dominant exposure category per `agentId` from the outcomes
   `EvidenceMappingService` reports. Simple math is fine here — it
   demonstrates the exposure-visibility feedback loop, it isn't an
   actuarial model and it never produces a premium figure.
3. **`EvidenceController`** — `GET /api/evidence`, `GET /api/exposure-summary`,
   and `GET /api/evidence/export` (a downloadable JSON audit bundle — the
   actual "export audit evidence" deliverable).
4. **`DashboardController`** — owns the `/` route (replace the day-0
   `com.aegis.HomeController` placeholder once this exists — delete that
   class so there's no route clash). Pulls a summary across all three
   slices (call their REST endpoints, or read shared H2 tables directly —
   whichever is simpler given the timeline).
5. **UI pages** — `templates/pipeline/evidence.html + exposure.html` (evidence list +
   exposure summary, with an "Export audit evidence" link) and
   `templates/dashboard.html` (the landing page: recent decisions, flagged
   count, evidence records, current exposure summary — make the end-to-end
   story decision → flag → evidence → exposure visually obvious, without
   ever implying a payout or a price was set). Both use the shared
   `nav`/`footer` fragments from `templates/layout.html`.

## Files you own

- `src/main/java/com/aegis/evidence/**`
- `src/main/resources/templates/pipeline/evidence.html`, `src/main/resources/templates/pipeline/exposure.html`
- `src/main/resources/templates/dashboard.html`
- `src/test/java/com/aegis/evidence/**`
- `src/main/java/com/aegis/HomeController.java` (delete it once your
  `DashboardController` takes over `/`)

## Do not touch

`com.aegis.contracts`, `templates/layout.html`, `static/css/aegis-ui.css`,
`pom.xml`, or another package's own files — raise it with the team instead.

## Getting a Claude session going

Use the "Person C" prompt from `docs/CLAUDE_PROMPTS.md` (or the project doc)
as the first message in your own Claude session, after cloning and checking
out `feature/evidence`.
