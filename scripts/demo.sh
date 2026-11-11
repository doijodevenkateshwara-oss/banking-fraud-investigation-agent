#!/usr/bin/env bash
# Policy-grounded fraud investigation demo against a running instance (synthetic data only).
#   ANTHROPIC_API_KEY=... CAPSTONE_DEMO_PASSWORD=demo-pass java -jar target/fraud-dispute-agent.jar &
#   CAPSTONE_DEMO_PASSWORD=demo-pass ./scripts/demo.sh
set -euo pipefail
BASE=${BASE:-http://localhost:8080}
PW=${CAPSTONE_DEMO_PASSWORD:?set CAPSTONE_DEMO_PASSWORD to the demo password}
J=(-s -H 'Content-Type: application/json')
pp() { python3 -m json.tool; }
step() { printf '\n\033[1;34m== %s\033[0m\n' "$*"; }

step "1. Intake: guide sample case (agent01)"
CASE=$(curl "${J[@]}" -u agent01:"$PW" -X POST "$BASE/api/cases" -d '{
  "maskedCustomerRef":"CUST-••••-4821","transactionRef":"TXN-2026-784102","amount":48750.00,"currency":"INR",
  "occurredAt":"2026-11-06T08:42:00+05:30","status":"POSTED","channel":"CARD_NOT_PRESENT",
  "maskedMerchant":"NORTHSTAR DIGITAL ••••73","issueType":"MERCHANT_NOT_RECOGNISED",
  "customerStatement":"I do not recognise this purchase and still have my card.",
  "syntheticDataConfirmed":true,"reviewRequired":true}' | python3 -c 'import sys,json; d=json.load(sys.stdin); print(d["caseId"]); print("   status:", d["status"], "| masked:", d["maskedCustomerRef"], file=sys.stderr)')
echo "   case: $CASE"

step "2. Unmasked card number is rejected at intake"
curl "${J[@]}" -u agent01:"$PW" -X POST "$BASE/api/cases" -d '{"maskedCustomerRef":"4111111111111111"}' \
  | python3 -c 'import sys,json; print("  ", json.load(sys.stdin)["fieldErrors"]["maskedCustomerRef"])'

step "3. Bounded investigation by the LangChain4j agent (investigator01)"
curl "${J[@]}" -u investigator01:"$PW" -X POST "$BASE/api/cases/$CASE/investigations" \
  | python3 -c 'import sys,json; r=json.load(sys.stdin)
print("   %s / %s risk / route %s / %s / confidence %.2f / mode %s" % (r["classification"], r["riskLevel"], r["recommendedRoute"], r["priority"], r["confidence"], r["mode"]))
print("   humanReviewRequired:", r["humanReviewRequired"], "| fallback:", r["fallbackReason"])
print("   missing information:", r["missingInformation"])
print("   rationale:", r["rationale"])'

step "4. Tool trace (operational only, no hidden reasoning)"
curl "${J[@]}" -u reviewer08:"$PW" "$BASE/api/cases/$CASE/tool-traces" \
  | python3 -c 'import sys,json; [print("   %s %-24s %-10s %4dms %s" % (t["traceId"], t["toolName"], t["status"], t["durationMs"], t["resultSummary"])) for t in json.load(sys.stdin)]'

step "5. Evidence with provenance"
curl "${J[@]}" -u reviewer08:"$PW" "$BASE/api/cases/$CASE/evidence" \
  | python3 -c 'import sys,json; [print("   %s %-11s %-8s %-28s %s (%s)" % (e["evidenceId"], e["type"], e["severity"], e["title"], e["provenance"], e["source"])) for e in json.load(sys.stdin)]'

step "6. Policy citations with exact metadata"
curl "${J[@]}" -u reviewer08:"$PW" "$BASE/api/cases/$CASE/citations" \
  | python3 -c 'import sys,json; [print("   %s v%s §%s %-12s score %.3f %s" % (c["documentId"], c["version"], c["section"], c["chunkId"], c["score"], "CITED" if c["cited"] else "")) for c in json.load(sys.stdin)]'

step "7. Reviewer cannot submit without a rationale (T12)"
curl "${J[@]}" -u reviewer08:"$PW" -X POST "$BASE/api/cases/$CASE/reviews" -d '{"disposition":"ESCALATE_FRAUD_INVESTIGATION","reason":""}' \
  | python3 -c 'import sys,json; print("  ", json.load(sys.stdin)["fieldErrors"])'

step "8. Human disposition (reviewer08) and audit event"
curl "${J[@]}" -u reviewer08:"$PW" -X POST "$BASE/api/cases/$CASE/reviews" -d '{"disposition":"ESCALATE_FRAUD_INVESTIGATION",
  "reason":"Independent device and location indicators support escalation. Authentication outcome requested as follow-up.",
  "checklist":["CUSTOMER_STATEMENT_REVIEWED","TOOL_EVIDENCE_CHECKED","POLICY_PASSAGES_OPENED","MISSING_INFORMATION_ACKNOWLEDGED","NO_AUTOMATIC_FINANCIAL_ACTION"]}' | pp
curl "${J[@]}" -u auditor01:"$PW" "$BASE/api/cases/$CASE/audit" \
  | python3 -c 'import sys,json; [print("   #%-3s %-28s %-15s %s" % (e["sequence"], e["eventType"], e["actorId"], e["summary"][:80])) for e in json.load(sys.stdin)]'
echo -n "   audit chain: "; curl "${J[@]}" -u auditor01:"$PW" "$BASE/api/audit/verify"; echo

step "9. Second case (duplicate processing)"
CASE2=$(curl "${J[@]}" -u investigator01:"$PW" -X POST "$BASE/api/cases" -d '{
  "maskedCustomerRef":"CUST-••••-2208","transactionRef":"TXN-2026-784333","amount":3420.50,"currency":"INR",
  "occurredAt":"2026-11-04T12:05:00+05:30","status":"POSTED","channel":"CARD_PRESENT","maskedMerchant":"URBANFRESH GROCERY ••••41",
  "issueType":"DUPLICATE_CHARGE","merchantRecognised":true,"customerStatement":"I was charged twice for the same grocery purchase.",
  "syntheticDataConfirmed":true,"reviewRequired":true}' | python3 -c 'import sys,json; print(json.load(sys.stdin)["caseId"])')
curl "${J[@]}" -u investigator01:"$PW" -X POST "$BASE/api/cases/$CASE2/investigations" \
  | python3 -c 'import sys,json; r=json.load(sys.stdin); print("   %s: %s -> %s (%s)" % (r["caseId"], r["classification"], r["recommendedRoute"], r["mode"]))'

step "Done. Open $BASE/cases/$CASE/investigation in the UI to walk the six screens."
