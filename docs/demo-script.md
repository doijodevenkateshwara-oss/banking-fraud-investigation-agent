# Demonstration script (≈10 minutes, guide §12.5)

Setup:

```bash
export CAPSTONE_DEMO_PASSWORD=demo-pass
export ANTHROPIC_API_KEY=…   # required: LangChain4j + Claude is the only processor
CAPSTONE_DB_URL='jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1' java -jar target/fraud-dispute-agent.jar
```

Without a key every investigation ends in SAFE_MANUAL_REVIEW, which is useful to show the safe-failure path (T07).
Live model output is not deterministic, so the exact confidence and wording can differ between runs.

1. **Problem, scope, safety boundary.** Read the footer banner and README header. The assistant may recommend a route,
   priority or evidence request; it never blocks, refunds, moves funds, files reports or contacts customers.
2. **Repository and configuration.** Show `src/main/java/com/bfs/fraudcase/` (layer packages), `application-example.yml`
   (model settings, safety flags) and `/actuator/health` (LangChain4j/Anthropic model provider and vector store).
3. **Create the sample case.** Sign in as `investigator01`, then go to Cases → *Load guide sample*. Point out the masked
   references, the SYNTHETIC DATA badge and the issue chips. Submitting `4111 1111 1111 1111` in the statement shows a
   field error.
4. **Run the investigation.** Click *Start investigation*. Show the plan steps, the 7 tool cards (status, duration,
   trace IDs) and the "operational trace only" banner. For a failure state, open the T08 case
   (`TXN-2026-784888`): getDeviceSignals shows TIMEOUT and compareRecentLocations shows NOT_FOUND.
5. **Evidence and provenance.** Open the Evidence workspace. Observed evidence, customer claims and the generated
   summary are shown separately. *Open redacted record* shows the stored tool result and trace hash.
6. **Policy citations.** Show the query, Top-K 4 and min 0.72. Show POL-CNP-017 v3.2 §3.2 `cnp-032` with its score and
   *Open passage*, and point out that the withdrawn v3.1 is listed as not citable. For abstention, open the T05 case
   (`TXN-2026-784444`).
7. **Structured recommendation.** Show classification, risk, route, priority, confidence, decision checks (schema
   PASS) and missing information ("authentication challenge outcome"), plus the evidence request.
8. **Human disposition.** Sign in as `reviewer08`, click *Open human review* and submit with an empty rationale to show
   the T12 error. Then select *Escalate fraud investigation*, enter a rationale, tick the checklist and submit. Open the
   Audit trail to show REVIEW_DISPOSITION_RECORDED with reviewer identity and timestamp. Sign in as `lead01` or
   `auditor01` to see "Hash chain intact".
9. **A second case.** As `investigator01`, create the duplicate-charge case (`TXN-2026-784333`,
   `URBANFRESH GROCERY ••••41`, DUPLICATE_CHARGE, merchant recognised) and start the investigation. The expected result
   is DUPLICATE_PROCESSING → PAYMENT_OPERATIONS.
10. **Tests and findings.** Run `mvn clean verify` (78 tests, offline with the test-scoped scripted model) and open
    `target/evaluation-report.md`. All 9 labelled cases pass and are deterministic. The known limitation is paraphrase
    recall with the small offline embedding model.

The API equivalent of steps 3–9 is `BASE=http://localhost:8080 ./scripts/demo.sh`.
