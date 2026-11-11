# Prompt change log

Prompts live in `src/main/resources/prompts/`. The output contract is in `schema/fraud-case-recommendation.schema.json`.

| Version | Date | Change | Reason / evidence |
|---|---|---|---|
| 1.0 | 2026-11-11 | Initial `fraud-system.txt`: role (triage assistant, not decision maker), allowed and forbidden actions, 8 grounding rules, classification → route table, JSON-only output. | Guide §6.2 prompt contract. |
| 1.0 | 2026-11-11 | `fraud-user.txt` variables: investigationGoal, plan, caseSummary (`<case_facts>`), intakeEvidence, customerStatement inside `<untrusted_data>`, policyQueryHint, guardrails, outputSchema, caseId. | Guide §6.2 variable table. Keeps untrusted text clearly delimited (T11). |
| 1.0 | 2026-11-11 | `followup-system.txt`: single-case scope, no actions, "not available" when the answer is not in context, case context wrapped as untrusted data. | US-05, T10. |
| 1.1 | 2026-11-11 | Retrieval query vocabulary aligned with policy wording ("customer denies card-not-present transaction", "newly observed device", "location inconsistent with recent trusted activity"). Chunk embeddings use section title + body (document title removed). | Calibration: the document-title prefix made every CNP chunk score alike. After the change, T01 retrieves §3.2 Initial fraud routing and FRD-009 §2.3/§2.4 above 0.72, and T05 still abstains (0.63). |
| 1.1 | 2026-11-11 | Added the explicit tool error handlers for LangChain4j (`toolExecutionErrorHandler`, `toolArgumentsErrorHandler`). They return structured JSON and never exception text. | No provider or exception details reach the model; this removed the LangChain4j default-handler warning. |
| 1.1 | 2026-11-11 | No prompt text changed. The rules processor and rules fallback were removed: LangChain4j + Anthropic is the only processor, and failed, malformed or guardrail-breaching model output now ends in SAFE_MANUAL_REVIEW. The scripted model moved to `src/test`. | Single AI framework (LangChain4j). `FailureModesTest` and `EvaluationSuiteTest` safety gate (3/3). |
