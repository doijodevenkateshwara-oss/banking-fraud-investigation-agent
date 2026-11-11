# Self-review

Answers to guide §12.4.

1. **Why is the workflow agentic rather than only generative?** The assistant follows a bounded
   plan → act → observe → retrieve → assess loop. The LangChain4j agent's model decides which allow-listed tools to
   call, observes their structured results, and retrieves policy before it recommends. The application validates every
   reference against what was actually executed in the session.
2. **Where does each fact come from?** Every `EvidenceItem` carries a provenance:
   - `INTAKE`: validated customer claims, labelled CLAIMED;
   - `TOOL`: tool results, with toolTraceId, request hash and timestamp;
   - `POLICY`: retrieved passages, with documentId, version, section, chunkId and score;
   - `REVIEWER`: dispositions, with reviewer ID, reason and checklist.

   Generated rationale is labelled as generated in the evidence workspace.
3. **What happens when a dependency fails?**
   - Tool: a structured TIMEOUT, NOT_FOUND or UNAVAILABLE result plus a missing-information entry.
   - Model: an audited MODEL_UNAVAILABLE event, then a safe manual-review result (case INCOMPLETE).
   - Vector store: retrieval abstains and health reports DOWN.
   - Database: a safe problem response is returned, and `failSafe` never leaves a case in ENRICHING.
4. **How do I prove one case cannot recall another?** Memory is keyed by a validated caseId, and follow-up context is
   built only from that case's records. `MemoryIsolationTest` stores a fact in case A, confirms it can be recalled in
   A, and shows it is neither recalled from B nor present in B's store.
5. **Absent, stale or contradictory policy?** When nothing meets the threshold, the system abstains: route
   MANUAL_REVIEW, a policy-gap entry, and the validator blocks procedural claims. Withdrawn versions are ingested but
   filtered out and shown as unavailable. Only the latest ACTIVE version of each document is kept. Contradictory
   evidence lowers confidence and routes to manual review.
6. **Why a recommendation rather than a decision?** Bank dispute outcomes affect customers and money, and the model
   output is probabilistic. No action tool exists. `humanReviewRequired` is enforced, and a reviewer must record a
   disposition and a rationale before a case is routed or closed.
7. **Tracing one action through the layers:** "Start investigation" goes through
   - `WebController.investigate`;
   - `InvestigationOrchestrator.start` (authorise, transition, plan);
   - the `FraudCaseProcessor` (`LangChain4jFraudCaseAgent`);
   - `EvidenceTools` → `ToolExecutor` → `SyntheticFixtureProvider` / `PolicyRetriever`;
   - `RecommendationValidator`;
   - the repositories and `AuditWriter`.

   The correlation ID appears in logs, traces, the recommendation and audit events.
8. **Both frameworks?** No. LangChain4j is the only AI framework and the only processor (there is no rules mode and no
   Spring AI adapter). The domain, contract, tools, policies and schema stay framework-neutral.
9. **What would change before a controlled bank sandbox?**
   - Real identity provider (OIDC) and per-case entitlements, in place of the in-memory demo users.
   - A managed database with WORM or ledger-backed audit, in place of H2 triggers.
   - A managed vector store with access control.
   - An approved and evaluated model endpoint with data-residency controls.
   - Prompt and evaluation governance, including red-team injection suites.
   - Retention policies, and DLP on logs.
   - Rate limiting and asynchronous investigations with progress events.
   - Model-risk validation of the decision table and thresholds.
   - Accessibility audit with assistive technologies.

## Residual risks and limitations
- The small embedding model limits paraphrase recall (8/9 on the labelled set). Misses abstain rather than mis-cite.
- The prompt-injection detector is a heuristic flag. Protection comes from the architecture: no action tools, a
  validator, application-owned state and human review.
- Live Claude behaviour is not covered by automated tests, which never call live services. The same validator and
  safe manual-review path bound its output. Without an API key no AI recommendation can be produced.
- Investigations run synchronously within the HTTP request. This is acceptable for synthetic fixtures; production would
  need asynchronous jobs.
- Intake infers `cardPossession` from explicit statement phrases only when the field is omitted (the API sample body
  has no such field). The UI always asks explicitly.
