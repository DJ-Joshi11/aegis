# `com.aegis.contracts`

Shared Java records used across all three slices. **Not owned by one person —
owned by the whole team.**

| Record | Produced by | Consumed by |
|---|---|---|
| `DecisionEvent` | Ingestion | Detection, Payout |
| `FlaggedDecision` | Detection | Payout |
| `PayoutResult` | Payout | Dashboard |

## Rule

Never change a record inside your own feature branch. If a field genuinely
needs to be added or changed, raise it with the other two first, land it in
its own small PR that everyone reviews, then keep building. This is what
lets all three packages be built in parallel without merge conflicts —
breaking it defeats the point.
