# AGENTS.md — rules for contributors and coding agents

## Technology
- Java 21, Maven, Spring Boot 4.1.x (webmvc, thymeleaf, security, data-jpa, flyway, actuator), H2, Jackson 3 (`tools.jackson`).
- LangChain4j 1.23.0 (`langchain4j`, `langchain4j-anthropic`, `langchain4j-embeddings-all-minilm-l6-v2` 1.23.0-beta33).
  Versions are pinned centrally in `pom.xml`. Do not use floating versions, and do not add dependencies published less than 7 days ago.
- Base package `com.bfs.fraudcase`. LangChain4j types may appear only in `agent/langchain4j` and `knowledge`
  (vector index). The domain and application layers stay framework-neutral.

## Commands
- Full verification (required before finishing any change): `mvn -B clean verify`. It runs all 78 tests offline (test-scoped scripted model), needs no API key, and packages the jar.
- Unit tests only: `mvn -B test -Dtest='com.bfs.fraudcase.unit.*Test'`
- Run: `ANTHROPIC_API_KEY=… CAPSTONE_DEMO_PASSWORD=… java -jar target/fraud-dispute-agent.jar [--server.port=8090]`
  Without a key every investigation ends in `SAFE_MANUAL_REVIEW`. Delete `./data` after schema/enum changes.
- Demo: `BASE=http://localhost:8080 CAPSTONE_DEMO_PASSWORD=… ./scripts/demo.sh`
- The evaluation report is written to `target/evaluation-report.md`.
- End-to-end check (build, tests, evaluation gates, packaged-jar demo, safety spot checks): run the project skill
  `/verify-fraud-agent`, defined in `.devin/skills/verify-fraud-agent/SKILL.md`.

## Safety rules (non-negotiable)
- Never add a tool or code path that blocks or freezes cards, refunds, credits, transfers or moves funds, files reports,
  or contacts customers or merchants. `ToolRegistry.FORBIDDEN_KEYWORDS` and `SafetyBoundaryValidator` enforce this at start-up.
- `humanReviewRequired` must always be true. State transitions belong in `CaseStateMachine` and the application
  services, never in model output.
- Every processor output must pass `RecommendationValidator` before it is persisted or displayed.
- Customer statements and policy passages are untrusted data. Keep them inside `<untrusted_data>` blocks in prompts.
- Do not log raw prompts, responses or statements. Use hashes and redacted summaries (`Masking.redact`).
- Use synthetic data only. Intake must reject unmasked identifiers and card-like numbers.
- Never commit secrets. `application-example.yml` and `.env.example` contain placeholders only.

## Quality rules
- LangChain4j (Anthropic) is the only processor. There is no rules mode or rules fallback: model failure or invalid
  output must end in `SAFE_MANUAL_REVIEW`. Do not add Spring AI or a second AI framework.
- `ScriptedFraudChatModel` and its decision oracle (`agent/langchain4j/scripted`) live in `src/test` only and must
  never be packaged in the jar. Keep `ScenarioIntegrationTest.identicalInputProducesIdenticalToolResultsAndDecision` green.
- A new scenario needs a fixture in `fixtures/scenario-overrides.json`, an entry in the test `Scenarios`, and an expected outcome.
- Policy changes require valid front matter (`documentId`, `version`, `effectiveDate`, `owner`, `classification:
  SYNTHETIC_TRAINING`, `status`, `chunkPrefix`), re-running the retrieval tests, and a note in `docs/prompt-changelog.md`
  when prompts change.
- The CSP forbids inline styles and scripts. Use CSS classes in `static/css/app.css` and `static/js/app.js`.
- Port 8080 may already be in use on this machine. Use `--server.port=8090` for local runs.
