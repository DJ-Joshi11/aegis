# `com.aegis.payout` — owned by Person C

## Scope

Turn a flagged decision into an automatic payout, keep a running pricing
signal from the carrier's own AI error rate, and serve the main dashboard —
the single page reviewers will look at first.

## Build

1. **`PayoutEngineService`** — listens for `FlaggedDecision` via
   `@EventListener`; if `flagged` is true (or an SLA window, e.g. 48h,
   is breached with no resolution), computes a payout amount with a
   simple parametric rule and publishes a `PayoutResult`.
2. **`PricingFeedbackService`** — keeps a running "agent error rate" per
   `agentId` from `PayoutResult`s seen so far, and a simple
   premium-adjustment number derived from it. Simple math is fine here —
   it demonstrates the feedback-loop concept, it doesn't need to be
   actuarially real.
3. **`PayoutController`** — `GET /api/payouts`.
4. **`DashboardController`** — owns the `/` route (replace the day-0
   `com.aegis.HomeController` placeholder once this exists — delete that
   class so there's no route clash). Pulls a summary across all three
   slices (call their REST endpoints, or read shared H2 tables directly —
   whichever is simpler given the timeline).
5. **UI pages** — `templates/payout/payouts.html` (payout list + pricing
   signal) and `templates/dashboard.html` (the landing page: recent
   decisions, flagged count, payouts triggered, current premium signal —
   make the end-to-end story decision → flag → payout → price visually
   obvious). Both use the shared `nav`/`footer` fragments from
   `templates/layout.html`.

## Files you own

- `src/main/java/com/aegis/payout/**`
- `src/main/resources/templates/payout/**`
- `src/main/resources/templates/dashboard.html`
- `src/test/java/com/aegis/payout/**`
- `src/main/java/com/aegis/HomeController.java` (delete it once your
  `DashboardController` takes over `/`)

## Do not touch

`com.aegis.contracts`, `templates/layout.html`, `static/css/aegis.css`,
`pom.xml`, or another package's own files — raise it with the team instead.

## Getting a Claude session going

Use the "Person C" prompt from `docs/CLAUDE_PROMPTS.md` (or the project doc)
as the first message in your own Claude session, after cloning and checking
out `feature/payout`.
