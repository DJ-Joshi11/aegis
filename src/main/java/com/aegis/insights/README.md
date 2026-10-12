# `com.aegis.insights` — analytics layer

Read-only statistical inference over everything the pipeline has seen.
It never writes to the decision, flag or evidence tables.

| File | Role |
|---|---|
| `InsightsEngine` | Pure function: decisions + flags + verdicts → `InsightsReport`. All statistics live here. |
| `Stats` | Wilson interval, two-proportion z-test, normal CDF, Population Stability Index. |
| `ReasoningQualityChecker` | Rule checks on reasoning trails (thin, no figures, contradiction, low-confidence auto-decision). |
| `InsightsReport` | JSON shape of `GET /api/insights`. |
| `ReviewVerdict`, `ReviewService` | Human UPHELD / OVERTURNED verdicts — the only ground truth. |
| `SimulatedReviewer` | Demo-only stand-in for human reviewers. Disable with `aegis.insights.simulated-reviewer=false`. |
| `InsightsController`, `InsightsViewController` | `/api/insights`, `/api/reviews`, `/api/reviews/queue`, `POST /api/reviews/{id}`, `/insights` page. |

Thresholds are in `application.properties` under `aegis.insights.*`.
Tests: `src/test/java/com/aegis/insights/` (engine scenarios with known planted patterns).
