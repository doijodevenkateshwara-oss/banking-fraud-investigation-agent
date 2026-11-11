---
name: verify-fraud-agent
description: Build, test and demo-verify the payment fraud investigation agent (offline tests; live demo needs ANTHROPIC_API_KEY)
allowed-tools:
  - read
  - grep
  - glob
permissions:
  allow:
    - Exec(mvn)
    - Exec(curl)
    - Exec(java -jar target/fraud-dispute-agent.jar)
    - Exec(./scripts/demo.sh)
  deny:
    - Write(src/main/resources/policies/**)
    - Write(src/main/resources/fixtures/**)
---

Verify that the payment fraud investigation and dispute resolution agent in this repository still builds, passes its
tests, and behaves correctly end to end. LangChain4j is the only processor. Tests use a test-scoped scripted model
and run offline; the packaged jar always calls Anthropic, so the live demo (step 4) needs `ANTHROPIC_API_KEY`.

This is a **verification-only** skill. Do not change source code, policies or fixtures. If something fails, stop,
report the failure with file and line references, and propose a fix. Do not apply the fix.

## Steps

1. **Build and test (definition of done).**
   - Run `mvn -B clean verify`. It runs offline and needs no API key. The run takes a few minutes, so execute it as a
     background command and wait for it to finish.
   - Expect `Tests run: 78, Failures: 0, Errors: 0` (or more tests, if some have been added) and `BUILD SUCCESS`.
   - On failure, read `target/surefire-reports/<TestClass>.txt` and report the failing assertion.

2. **Check the evaluation gates.**
   - Read `target/evaluation-report.md`.
   - Report schema reliability, decision accuracy, unexpected tool calls, Recall@4, abstention and safety.
   - Flag any regression against `docs/evaluation-summary.md`. The baseline is 9/9, 9/9, 0 unexpected calls,
     8/9, 2/2 and 3/3.

3. **Start the packaged application.** Port 8080 may already be in use on this machine, so use 8090.
   - Start the jar as a background process (pass `ANTHROPIC_API_KEY` through only if the user provided one):
     `CAPSTONE_DEMO_PASSWORD=demo-pass CAPSTONE_DB_URL='jdbc:h2:mem:verify;DB_CLOSE_DELAY=-1' java -jar target/fraud-dispute-agent.jar --server.port=8090`
   - Poll `curl -s -u lead01:demo-pass localhost:8090/actuator/health` until the overall status is `UP`.
     `vectorStore` must be UP. `modelProvider` is UP with a key and `DEGRADED` without one (expected).

4. **Run the demo** (only with an API key; otherwise report it as SKIPPED and instead check that one investigation
   returns `mode: SAFE_MANUAL_REVIEW`, route `MANUAL_REVIEW`, `humanReviewRequired: true` and no stack trace).
   - Command: `BASE=http://localhost:8090 CAPSTONE_DEMO_PASSWORD=demo-pass ./scripts/demo.sh`
   - Live model output is not deterministic. Treat the expectations below as checks to report, not hard gates,
     except `humanReviewRequired`, T12 and the audit chain, which must always hold.
   - Confirm each of these:
     - The guide sample case (TXN-2026-784102) is classified **UNAUTHORISED_TRANSACTION / HIGH / FRAUD_INVESTIGATION / P1**.
     - `humanReviewRequired` is true.
     - Missing information lists "authentication challenge outcome".
     - The trace shows 7 tool calls, all SUCCEEDED.
     - The citations include `POL-CNP-017 v3.2 §3.2 cnp-032` with a score of at least 0.72.
     - A review without a rationale is rejected (T12).
     - After the reviewer disposition the case is **ROUTED**, and the audit trail contains `REVIEW_DISPOSITION_RECORDED`.
     - `/api/audit/verify` returns `"intact":true`.
     - The duplicate-charge case is classified DUPLICATE_PROCESSING → PAYMENT_OPERATIONS.

5. **Safety spot checks** (use `curl` with `-u <user>:demo-pass`):
   - `agent01` POST `/api/cases/{id}/investigations` returns **403**.
   - POST `/api/cases` with `"maskedCustomerRef":"4111111111111111"` returns **400** with `fieldErrors.maskedCustomerRef`.
   - No error response body contains a stack trace or the word `Exception`.

6. **Clean up.** Stop the application process you started. Do not touch other processes, in particular whatever
   is listening on port 8080.

## Report format

Return a short table with one row per step (Build/test, Evaluation gates, Health, Demo, Safety checks) and a column
for PASS or FAIL with one line of evidence. Follow it with a list of any regressions or risks you found. Keep the
report under 25 lines.
