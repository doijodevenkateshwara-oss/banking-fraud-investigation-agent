# Runbook — how to run and what to configure

This guide covers running the fraud investigation agent locally and the configuration it needs.
For architecture and API details see [README.md](README.md) and [docs/architecture.md](docs/architecture.md).

## Prerequisites

- **Java 21**
- **Maven 3.8+**
- An **Anthropic API key** for live AI recommendations (optional for build/tests; required for real investigations)

## Configuration

All configuration lives in `src/main/resources/application.yml` with placeholders resolved from
environment variables. Copy [.env.example](.env.example) to `.env` for local development — never commit `.env`.

### Required / recommended environment variables

| Variable | Required | Default | Purpose |
|---|---|---|---|
| `ANTHROPIC_API_KEY` | For live AI | *(empty)* | Anthropic API key. If empty, the model is `UnconfiguredChatModel` and every investigation ends in `SAFE_MANUAL_REVIEW`. |
| `CAPSTONE_DEMO_PASSWORD` | Recommended | *(random, printed once)* | Shared password for the synthetic demo users. |
| `CAPSTONE_MODEL_NAME` | No | `claude-sonnet-4-5` | Anthropic model name. |
| `CAPSTONE_DB_URL` | No | `jdbc:h2:file:./data/fraudcase;AUTO_SERVER=FALSE` | JDBC URL. Use `jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1` for a throw-away in-memory DB. |
| `CAPSTONE_FIXED_CLOCK` | No | *(empty)* | Fixed ISO-8601 timestamp for reproducible demos/SLA targets. |

### Tunables (defaults in `application.yml`)

These can be overridden with `--capstone.<property>=<value>` on the command line or via relaxed-binding
environment variables (e.g. `CAPSTONE_RETRIEVAL_TOPK`, `CAPSTONE_MEMORY_MAXMESSAGES`).

| Property | Default | Meaning |
|---|---|---|
| `capstone.retrieval.top-k` | `4` | Max policy passages retrieved per query. |
| `capstone.retrieval.similarity-threshold` | `0.72` | Minimum cosine similarity for a passage to be cited. |
| `capstone.memory.max-messages` | `12` | Max messages kept in case-scoped follow-up memory. |
| `capstone.tools.timeout-ms` | `2500` | Per-tool execution timeout. |
| `capstone.tools.deterministic-seed` | `fraud-training-v1` | Seed for synthetic fixture generation. |
| `capstone.model.timeout-seconds` | `60` | Anthropic request timeout. |
| `capstone.model.temperature` | `0.0` | Sampling temperature (kept at 0 for determinism). |
| `capstone.model.max-tokens` | `2048` | Max output tokens. |
| `capstone.model.max-tool-invocations` | `12` | Bound on tool calls per investigation. |

### Safety switches — do not change

Start-up **fails** if any of these are violated:

- `capstone.human-review-required` must stay `true`
- `capstone.allow-financial-actions` must stay `false`
- `capstone.allow-customer-contact` must stay `false`
- No registered tool may look like a forbidden action (block/freeze/refund/transfer/contact)

## Build

```bash
mvn clean verify
```

Runs compile, all 78 offline tests, and packages `target/fraud-dispute-agent.jar`. No API key needed.

## Run

```bash
export CAPSTONE_DEMO_PASSWORD=change-me-local-only
export ANTHROPIC_API_KEY=sk-ant-...        # omit for safe-manual-review-only mode
java -jar target/fraud-dispute-agent.jar   # http://localhost:8080
```

Useful variants:

```bash
# Throw-away in-memory DB, custom port
CAPSTONE_DB_URL='jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1' java -jar target/fraud-dispute-agent.jar --server.port=8090

# Fixed clock for reproducible SLA/demo timestamps
CAPSTONE_FIXED_CLOCK=2026-11-06T03:15:00Z java -jar target/fraud-dispute-agent.jar
```

### Live run with Anthropic (example used 2026-11-11)

1. Create `.env` in the repo root (gitignored — never commit it):

   ```bash
   ANTHROPIC_API_KEY=sk-ant-...
   CAPSTONE_DEMO_PASSWORD=demo-pass
   ```

2. Start the app on port 8090 with a throwaway in-memory DB:

   ```bash
   set -a; . ./.env; set +a
   CAPSTONE_DB_URL='jdbc:h2:mem:live;DB_CLOSE_DELAY=-1' \
     java -jar target/fraud-dispute-agent.jar --server.port=8090
   ```

3. Verify health — `modelProvider` must be `UP` (it is `DEGRADED` without a key):

   ```bash
   curl -s localhost:8090/actuator/health | python3 -m json.tool
   ```

4. Open http://localhost:8090 and sign in as `investigator01` / `demo-pass`, then
   **Cases → Load guide sample → Start investigation** for a live Claude investigation.

Notes: the in-memory DB means all cases are lost on restart — omit `CAPSTONE_DB_URL` to use the
persistent `./data` file DB instead. Live model output is not deterministic; `humanReviewRequired`
is always `true` regardless.

- Default DB is a file-based H2 database under `./data/`. Delete `./data` after schema or enum changes.
- If port 8080 is busy, add `--server.port=8090`.
- If `CAPSTONE_DEMO_PASSWORD` is unset, a random password is generated and printed once at start-up.

## Demo users

All users are synthetic and share `CAPSTONE_DEMO_PASSWORD`.

| User | Roles | Can |
|---|---|---|
| `agent01` | SERVICE_AGENT | create cases |
| `investigator01` | SERVICE_AGENT, INVESTIGATOR | create cases, start/restart investigations, follow-up questions |
| `reviewer08` | REVIEWER | record a disposition |
| `lead01` | REVIEWER, TEAM_LEAD | reviewer powers + OVERRIDE; verify audit chain |
| `auditor01` | AUDITOR | view audit trail, verify hash chain |

Separation of duties: the investigator of a case cannot record its disposition.

## Verify it is working

```bash
# Health (no credentials needed)
curl -s localhost:8080/actuator/health

# Expect overall UP; modelProvider DEGRADED without ANTHROPIC_API_KEY, UP with a valid key.

# Login page
open http://localhost:8080
```

Then sign in as `investigator01`, load the guide sample, and start an investigation.

## Behaviour without an API key

The app still starts and serves the UI/API, but:

- `/actuator/health` shows `modelProvider: DEGRADED`
- Every investigation returns `SAFE_MANUAL_REVIEW` with route `MANUAL_REVIEW`, confidence `0.0`, `humanReviewRequired: true`, and the case ends `INCOMPLETE`
- This is the intended safe failure mode — it is not an error

## Troubleshooting

| Symptom | Likely cause / fix |
|---|---|
| `403` on investigator/review endpoints | Wrong role — check the demo-users table. `agent01` can only create cases. |
| Investigations always `SAFE_MANUAL_REVIEW` | `ANTHROPIC_API_KEY` missing/invalid, or model output failed validation. Check health and logs. |
| Random password each start | `CAPSTONE_DEMO_PASSWORD` not set. |
| Port already in use | Add `--server.port=8090`. |
| Old rows fail to load after upgrade | Delete `./data` — old `RULES`/`RULES_FALLBACK` enum values are gone. |
| Flyway migration errors | Ensure `./data` is clean or the DB URL points to a fresh database. |

## Demo script

```bash
CAPSTONE_DEMO_PASSWORD=... BASE=http://localhost:8080 ./scripts/demo.sh
```

Requires a running server with `ANTHROPIC_API_KEY` for live recommendations.
