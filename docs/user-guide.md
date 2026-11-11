# User guide — screens and end-to-end flow

This guide walks through every screen of the FraudOps AI web UI and the complete lifecycle of a fraud case,
from intake to a recorded human disposition. All data is synthetic training data.

> **Safety boundary.** The assistant recommends a route, priority and evidence requests only. It cannot block
> cards, refund, move funds, file reports or contact customers — no such tool exists. Every recommendation
> requires an authorised human reviewer before a case is routed or closed.

## Roles and sign-in

All demo users share the password in `CAPSTONE_DEMO_PASSWORD` (see [RUNBOOK.md](../RUNBOOK.md)).

| User | Roles | Typical use |
|---|---|---|
| `agent01` | SERVICE_AGENT | Create cases only |
| `investigator01` | SERVICE_AGENT, INVESTIGATOR | Create cases, run investigations, ask follow-up questions |
| `reviewer08` | REVIEWER | Open and record human review dispositions |
| `lead01` | REVIEWER, TEAM_LEAD | Reviewer powers + OVERRIDE disposition + audit verification |
| `auditor01` | AUDITOR | Read audit trail and verify the hash chain |

Separation of duties: the investigator who ran a case cannot record its disposition.

## End-to-end flow

```
Sign in → Cases → Create case → (optional draft) → Start investigation
   → Agent investigation (plan + tool trace) → Recommendation
   → Evidence workspace / Policy citations (supporting detail)
   → Reviewer opens Human review → records disposition
   → Audit trail verifies the hash chain → case ROUTED or CLOSED
```

Case states: `NEW → ENRICHING → READY_FOR_REVIEW → UNDER_REVIEW → ROUTED → CLOSED`,
with `INCOMPLETE` for missing/contradictory evidence or a failed model call (restarts allowed).
Every step appends an event to the append-only, hash-chained audit trail.

## Page-by-page

### 1. Sign in — `/login`

Username + password form for the five synthetic demo users. Successful login redirects to `/cases`.
Repeated failures simply return "Invalid username or password" — no stack traces.

### 2. Cases — `/cases`

The case list for the signed-in user. Shows each case ID, status badge, classification/route summary and
timestamps. The **Load guide sample** button (`/cases/new?sample=true`) opens a pre-filled intake form with
the guide's T01 sample transaction (`TXN-2026-784102`).

### 3. Create fraud case — `/cases/new`

Intake form capturing only the facts needed to investigate: masked customer/merchant references,
transaction reference, amount/currency, timestamp, channel, issue type, the customer's statement, and three
mandatory attestations (synthetic data, human review required).

- **Validation is strict:** unmasked identifiers and card-like numbers (Luhn check) are rejected with
  per-field errors; instruction-like text in the statement is flagged as a suspected prompt injection.
- Buttons: **Save draft** (session-only), **Create case**, and **Start investigation**
  (investigators only — creates the case and immediately runs the bounded agent analysis).
- The left "CASE WORKFLOW" stepper shows the four stages: intake → investigation → recommendation →
  human review, with evidence/citations/audit as substeps.

### 4. Case intake record — `/cases/{caseId}`

Read-only view of the validated, masked intake. The customer statement is labelled as a *claim*, not
verified evidence. Investigators see **Start investigation** / **Restart investigation** here when the
case is `NEW` or `INCOMPLETE`.

### 5. Agent investigation — `/cases/{caseId}/investigation`

The visible operational trace of one bounded analysis run:

- **Investigation plan** — the deterministic five-step plan with per-step status and any unresolved
  questions (e.g. "authentication challenge outcome").
- **Tool execution** — one card per tool call (`getTransactionDetails`, `getDeviceSignals`,
  `compareRecentLocations`, `getMerchantRiskProfile`, `getPriorDisputes`, `retrievePolicyEvidence`,
  `calculateCaseSla`) with status, duration, redacted result summary and trace ID. Banner: *"Operational
  trace only · no hidden reasoning."*
- **Case-scoped follow-up** — investigators and reviewers can ask questions answered only from this
  case's stored record; answers cannot change the recommendation.
- If the model is unavailable or output is rejected, an amber banner shows *Safe manual review applied*
  with the reason, and the case lands in `INCOMPLETE` (confidence 0, route `MANUAL_REVIEW`).

### 6. Agent recommendation — `/cases/{caseId}/recommendation`

The advisory output, always marked *"Advisory output only"*:

- Risk banner: risk level, classification, recommended route, priority, confidence %, mode and run number.
- **Grounded rationale** with links to the evidence items and policy citations it relies on.
- **Decision checks**: counts of linked evidence, citations, missing information, contradictions, traced
  tool calls, and schema validation (`PASS`).
- **Recommended evidence requests** — suggestions for the reviewer only.
- `humanReviewRequired = true` is always displayed. Reviewers in `READY_FOR_REVIEW`/`UNDER_REVIEW` get the
  **Open human review** button; others see **View review status**.

### 7. Evidence workspace — `/cases/{caseId}/evidence`

Three clearly separated groups:

- **Observed evidence** — tool results with evidence ID, source, provenance (tool trace ID, request hash,
  timestamp) and severity.
- **Customer claims** — validated intake statements, labelled as unverified claims.
- **Generated summary** — model-written text, chipped `MODEL-GENERATED` (or `MANUAL-REVIEW`), with a
  reminder that observed evidence above is authoritative.

Clicking a record opens `/cases/{caseId}/evidence/{evidenceId}` — the underlying redacted record with its
provenance and trace hash.

### 8. Policy citations — `/cases/{caseId}/citations`

The retrieval record behind the recommendation:

- The exact policy query used, Top-K and minimum similarity threshold (0.72).
- Each passage with document ID, version, section, chunk ID and score; **Open passage** links to the full
  text at `/policies/{chunkId}`.
- Withdrawn/superseded versions are listed as *Unavailable* and excluded from retrieval.
- When nothing passes the threshold an explicit **Abstention** banner appears: the assistant makes no
  policy claim and routes to manual review. This is the designed safe outcome (scenario T05).

### 9. Policy library — `/policies`

The approved synthetic policy pack ingested at start-up (POL-CNP-017, POL-DUP-006, POL-FRD-009,
POL-MDS-012, POL-OPS-004), with versions, effective dates and status. Withdrawn versions are visibly
marked and never retrieved.

### 10. Human review — `/cases/{caseId}/review`

Where the final disposition is owned by a human:

- **Recorded dispositions** table once reviews exist (reviewer, disposition, final route, reason, time).
- The reviewer selects a disposition (ESCALATE / REQUEST_INFORMATION / REROUTE / CLOSE / OVERRIDE for team
  leads), writes a rationale (minimum 10 characters — empty submissions are rejected, scenario T12), and
  must tick all five checklist items.
- Missing information from the investigation is displayed and must be acknowledged.
- An audit-event preview shows what will be recorded: `REVIEW_DISPOSITION_RECORDED`, reviewer identity,
  timestamp, append-only, payload hashed. Submitting transitions the case to `ROUTED`, `CLOSED` or
  `INCOMPLETE`. Routing is a label — nothing is executed.

### 11. Review queue — `/review-queue`

Worklist of cases awaiting a reviewer disposition (`READY_FOR_REVIEW`, `UNDER_REVIEW`, `INCOMPLETE`)
with priority and age, for reviewers and team leads.

### 12. Audit trail — `/cases/{caseId}/audit`

Append-only, hash-chained event log for the case: intake, investigation, recommendation, review and
state transitions. Each row shows sequence, event type, actor, UTC timestamp, redacted summary and the
SHA-256 payload hash. For `auditor01`/`lead01` a banner reports **Hash chain intact across N events**
(or the first broken event). Payloads are never stored — only hashes.

### 13. Error page

Friendly error pages for 403/404/500-style failures. No stack traces or exception details are ever shown.

## Following one case end to end

1. **Intake** — `agent01` or `investigator01` submits the form (or `POST /api/cases`). The statement is
   stored separately from the sanitised version; injection-like text is flagged and audited.
2. **Investigation** — an investigator clicks *Start investigation*. The LangChain4j agent (Claude via
   `langchain4j-anthropic`) runs a bounded tool loop: it calls only allow-listed read-only tools, each
   executed by `ToolExecutor` with a 2.5 s timeout, structured failure results and a redacted trace.
3. **Retrieval** — `retrievePolicyEvidence` embeds the query with local all-MiniLM-L6-v2 and searches the
   in-memory vector index: top 4, min score 0.72, ACTIVE + currently effective + latest version only.
4. **Recommendation** — the model's JSON is mapped explicitly and validated by `RecommendationValidator`
   (enums, confidence range, caseId match, only-session evidence/trace/citation IDs, no action claims,
   route permitted by the classification table, `humanReviewRequired` forced true). Application facts win
   over model claims. Any failure → audited safe manual review (`INCOMPLETE`, confidence 0).
5. **Review** — a *different* user with REVIEWER opens the case from the review queue, records a
   disposition with rationale and checklist. The case becomes `ROUTED` or `CLOSED`.
6. **Audit** — every step is in the hash-chained trail; `auditor01` verifies integrity at any time.

## API equivalent

Every screen has a REST counterpart under `/api` (HTTP Basic, RFC 9457 errors): `POST /api/cases`,
`POST /api/cases/{id}/investigations`, `GET .../investigation|recommendation|evidence|citations|audit`,
`POST .../messages`, `POST .../reviews`. See [README.md](../README.md#api) for the full table, or run
`BASE=http://localhost:8090 CAPSTONE_DEMO_PASSWORD=demo-pass ./scripts/demo.sh` for a scripted walkthrough.
