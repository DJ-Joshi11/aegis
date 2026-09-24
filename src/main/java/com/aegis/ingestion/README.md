# `com.aegis.ingestion` — owned by Person A

## Scope

Ingest AI-agent decisions (mocked, shaped like a real Guidewire Qusar
agent's decision-log event) and turn them into a clean, published
`DecisionEvent` the rest of the app can react to.

## Build

1. **`MockDecisionGenerator`** — a scheduled job or a `POST /api/decisions`
   endpoint that creates sample AI-agent decisions (underwriting, claims,
   fraud-flag types), with plausible `agentId`, `decisionType`,
   `jurisdiction` values. This stands in for Qusar since we don't have
   carrier sandbox access — see the main `README.md`'s "How we're using
   Qusar" section for how to talk about this honestly.
2. **`DecisionIngestionService`** — validates an incoming `DecisionEvent`,
   persists it (JPA + H2) as the audit log, and publishes it as a Spring
   `ApplicationEvent` for the detection package.
3. **`DecisionController`** — `POST /api/decisions`, `GET /api/decisions`.
4. **UI page** — `src/main/resources/templates/ingestion/decision-feed.html`,
   using the shared `nav`/`footer` fragments from `templates/layout.html`
   (`th:replace="~{layout :: nav}"` / `~{layout :: footer}`). Shows a live
   table of incoming decisions: id, agent, type, outcome, jurisdiction, time.

## Files you own

- `src/main/java/com/aegis/ingestion/**`
- `src/main/resources/templates/ingestion/**`
- `src/test/java/com/aegis/ingestion/**`

## Do not touch

`com.aegis.contracts`, `templates/layout.html`, `static/css/aegis.css`,
`pom.xml`, or another package's files — raise it with the team instead.

## Getting a Claude session going

Use the "Person A" prompt from `docs/CLAUDE_PROMPTS.md` (or the project doc)
as the first message in your own Claude session, after cloning and checking
out `feature/ingestion`.
