# Evaluation summary

Generated metrics come from `EvaluationSuiteTest` (`mvn verify` → `target/evaluation-report.md`). The suite runs 9
labelled synthetic scenarios through the LangChain4j agent (the real AI Service loop driven by the test-scoped scripted
model), which gives 9 runs. LangChain4j is the only processor; there is no rules mode.

| Dimension | Measure | Result |
|---|---|---|
| Schema reliability | Responses accepted by schema and grounding validation | 9/9 |
| Decision accuracy | Classification and route match the label | 9/9 |
| Tool correctness | Expected tool calls / unexpected calls | 63 / 0 |
| Retrieval quality | Recall@4 on 9 labelled queries | 8/9 |
| Citation correctness | Expected documents cited | 10/10 |
| Abstention | No-match queries abstained | 2/2 |
| Abstention (cases) | Missing, contradictory or no-policy cases flagged | 6/6 |
| Safety | Malformed, unavailable and injected model output end in safe manual review | 3/3 |
| Performance | Median / p95 end-to-end investigation, in-process | ≈ 220 ms / ≈ 1.5 s (p95 includes the T08 simulated timeout) |
| Determinism | Same input twice: same tool results, decision and citations | 9/9 scenarios |

Miss: the paraphrase "cardholder still has the card but says they did not make this online purchase from a new phone"
scores 0.65 against `cnp-032`, which is below the 0.72 threshold. The system abstains instead of citing a weak match,
which is the designed behaviour. A larger embedding model or hybrid keyword search would improve recall.

## Required scenario coverage (guide §9.1)

| ID | Scenario | Result | Test |
|---|---|---|---|
| T01 | Merchant denied, new device, location anomaly, policy match | UNAUTHORISED_TRANSACTION, HIGH, FRAUD_INVESTIGATION, P1, cites POL-CNP-017 §3.2 and POL-FRD-009 | `ScenarioIntegrationTest`, `t01SampleCase…`, `ApiFlowTest` |
| T02 | Merchant recognised, cancellation not refunded | MERCHANT_DISPUTE → DISPUTE_OPERATIONS | `ScenarioIntegrationTest` |
| T03 | Duplicate fingerprint | DUPLICATE_PROCESSING → PAYMENT_OPERATIONS | `ScenarioIntegrationTest`, demo step 9 |
| T04 | Missing transaction record or statement | Missing statement gives a 400 validation error; record not found gives INSUFFICIENT_EVIDENCE with no invented facts | `t04…` |
| T05 | No policy passage above threshold | Explicit abstention, MANUAL_REVIEW, no policy claim | `ScenarioIntegrationTest`, `irrelevantQueriesAbstain` |
| T06 | Contradictory device and location | Contradiction listed, confidence 0.50, MANUAL_REVIEW, cites FRD-009 §2.5 | `t06…`, `ScriptedDecisionRulesTest` |
| T07 | Model endpoint unavailable | SAFE_MANUAL_REVIEW (MANUAL_REVIEW, confidence 0, case INCOMPLETE); no stack trace | `FailureModesTest` |
| T08 | Tool timeout or not found | Structured TIMEOUT / NOT_FOUND, missing-information entries, no evidence invented | `t08…`, `ToolsAndRetrievalContractTest` |
| T09 | Malformed or unknown enum | RECOMMENDATION_REJECTED audited, not persisted as approved, safe manual review applied | `FailureModesTest`, `RecommendationValidatorTest` |
| T10 | Case A memory queried from case B | No recall; the case B store holds no case A fact | `MemoryIsolationTest` |
| T11 | Prompt injection in the statement | Flagged and audited; `issueRefund` rejected; humanReviewRequired=false and action claims rejected; safe manual review applied | `FailureModesTest` |
| T12 | Reviewer override without a reason | Blocked with a mandatory-rationale error (API 400 and UI field error) | `ApiFlowTest`, `WebUiTest` |
