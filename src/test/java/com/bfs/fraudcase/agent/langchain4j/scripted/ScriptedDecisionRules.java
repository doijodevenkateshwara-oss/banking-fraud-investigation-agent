package com.bfs.fraudcase.agent.langchain4j.scripted;

import com.bfs.fraudcase.agent.common.RawRecommendation;
import com.bfs.fraudcase.domain.CaseFacts;
import com.bfs.fraudcase.domain.Classification;
import com.bfs.fraudcase.domain.DecisionModel;
import com.bfs.fraudcase.domain.PolicyContext;
import com.bfs.fraudcase.domain.RetrievedPassage;
import com.bfs.fraudcase.domain.RiskLevel;
import com.bfs.fraudcase.domain.Route;
import com.bfs.fraudcase.tools.InvestigationSession;
import com.bfs.fraudcase.tools.ToolRegistry;
import com.bfs.fraudcase.tools.dto.CaseSla;
import com.bfs.fraudcase.tools.dto.DeviceSignals;
import com.bfs.fraudcase.tools.dto.PolicyEvidence;
import com.bfs.fraudcase.tools.dto.LocationComparison;
import com.bfs.fraudcase.tools.dto.MerchantRiskProfile;
import com.bfs.fraudcase.tools.dto.PriorDisputes;
import com.bfs.fraudcase.tools.dto.TransactionDetails;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Test-only decision oracle for {@code ScriptedFraudChatModel}: the deterministic logic the fake LLM uses to choose
 * its answer, so labelled scenarios have stable expected outcomes. Not part of the production application.
 * <ul>
 *   <li>R-01 mandatory evidence missing → INSUFFICIENT_EVIDENCE / MANUAL_REVIEW</li>
 *   <li>R-02 confirmed duplicate fingerprint → DUPLICATE_PROCESSING / PAYMENT_OPERATIONS</li>
 *   <li>R-03 two or more independent high-severity anomalies → FRAUD_INVESTIGATION</li>
 *   <li>R-04 merchant recognised and fulfilment/cancellation/amount issue → DISPUTE_OPERATIONS</li>
 *   <li>R-05 no policy passage above threshold → MANUAL_REVIEW with explicit abstention</li>
 *   <li>R-06 contradictory device and location signals → reduced confidence / MANUAL_REVIEW</li>
 *   <li>R-07 authenticated, trusted device, consistent location → GENUINE_TRANSACTION / MANUAL_REVIEW</li>
 *   <li>R-08 no rule conclusive → INSUFFICIENT_EVIDENCE / MANUAL_REVIEW</li>
 *   <li>R-09 single high-severity anomaly → SUSPICIOUS_TRANSACTION / FRAUD_INVESTIGATION</li>
 * </ul>
 *
 * @author Venkateshwara Doijode
 */
public final class ScriptedDecisionRules {

    public static final String INTAKE = InvestigationSession.INTAKE;

    public RuleOutcome evaluate(CaseFacts f, EvidenceSnapshot s, List<String> missingInformation) {
        TransactionDetails txn = s.transaction();
        DeviceSignals device = s.device();
        LocationComparison loc = s.location();
        MerchantRiskProfile merchant = s.merchant();
        PriorDisputes disputes = s.disputes();

        boolean txnOk = txn != null && txn.succeeded() && txn.found();
        boolean deviceOk = device != null && device.succeeded();
        boolean locOk = loc != null && loc.succeeded();
        boolean newDevice = deviceOk && Boolean.FALSE.equals(device.deviceKnown());
        boolean trustedDevice = deviceOk && Boolean.TRUE.equals(device.deviceKnown()) && "HIGH".equals(device.trustLevel());
        boolean locationHigh = locOk && "HIGH".equals(loc.anomalyLevel());
        boolean locationNone = locOk && ("NONE".equals(loc.anomalyLevel()) || "LOW".equals(loc.anomalyLevel()));
        boolean authFailed = txnOk && "FAILED".equals(txn.authOutcome());
        boolean authSuccess = txnOk && "SUCCESS".equals(txn.authOutcome());
        boolean blocklisted = merchant != null && merchant.succeeded() && Boolean.TRUE.equals(merchant.blocklistMatch());

        List<String> ruleIds = new ArrayList<>();
        List<String> ruleFacts = new ArrayList<>();
        List<String> contradictions = new ArrayList<>();
        Set<String> requests = new LinkedHashSet<>();
        Set<String> cited = new LinkedHashSet<>(s.ids(INTAKE));
        Classification c;
        RiskLevel risk;
        double confidence;
        String finding;

        if (!txnOk) {
            ruleIds.add("R-01");
            ruleFacts.add("R-01: getTransactionDetails.status=" + (txn == null ? "NOT_INVOKED" : txn.status())
                    + ", found=" + (txn != null && txn.found()));
            c = Classification.INSUFFICIENT_EVIDENCE;
            risk = RiskLevel.MEDIUM;
            confidence = 0.30;
            requests.add("Request the transaction record from the card-processing platform");
            finding = "The transaction record could not be retrieved, so the case cannot be classified without inventing facts.";
        } else if (txn.duplicateFingerprintMatch()) {
            ruleIds.add("R-02");
            ruleFacts.add("R-02: duplicateFingerprintMatch=true, duplicateOf=" + txn.duplicateOfRef());
            cited.addAll(s.ids(ToolRegistry.GET_TRANSACTION_DETAILS));
            c = Classification.DUPLICATE_PROCESSING;
            risk = RiskLevel.LOW;
            confidence = 0.90;
            requests.add("Request settlement-ledger confirmation of the duplicate posting");
            finding = "The payment fingerprint, merchant and amount match an earlier posting (" + ref(s, ToolRegistry.GET_TRANSACTION_DETAILS)
                    + "), which indicates duplicate processing rather than fraud.";
        } else if (Boolean.TRUE.equals(f.merchantRecognised()) && DecisionModel.merchantDisputeIssues().contains(f.issueType())) {
            ruleIds.add("R-04");
            ruleFacts.add("R-04: merchantRecognised=true, issueType=" + f.issueType());
            cited.addAll(s.ids(ToolRegistry.GET_TRANSACTION_DETAILS));
            cited.addAll(s.ids(ToolRegistry.GET_MERCHANT_RISK_PROFILE));
            c = Classification.MERCHANT_DISPUTE;
            risk = RiskLevel.LOW;
            confidence = 0.85;
            requests.add("Request proof of cancellation, delivery tracking or the agreed amount from the customer");
            finding = "The customer recognises the merchant and disputes " + describe(f) + " " + ref(s, INTAKE)
                    + "; the transaction itself was authenticated " + ref(s, ToolRegistry.GET_TRANSACTION_DETAILS) + ".";
        } else if (!deviceOk && !locOk) {
            ruleIds.add("R-01");
            ruleFacts.add("R-01: getDeviceSignals.status=" + (device == null ? "NOT_INVOKED" : device.status())
                    + ", compareRecentLocations.status=" + (loc == null ? "NOT_INVOKED" : loc.status()));
            cited.addAll(s.ids(ToolRegistry.GET_TRANSACTION_DETAILS));
            c = Classification.INSUFFICIENT_EVIDENCE;
            risk = RiskLevel.MEDIUM;
            confidence = 0.35;
            requests.add("Request device session and recent-location records once the synthetic services are available");
            finding = "Both device and location evidence are unavailable, so unauthorised use cannot be assessed; "
                    + "no values have been assumed.";
        } else if (trustedDevice && locationHigh) {
            ruleIds.add("R-06");
            ruleFacts.add("R-06: deviceKnown=true, trustLevel=HIGH, location.anomalyLevel=HIGH, distanceKm=" + loc.distanceKm());
            cited.addAll(s.ids(ToolRegistry.GET_DEVICE_SIGNALS));
            cited.addAll(s.ids(ToolRegistry.COMPARE_RECENT_LOCATIONS));
            cited.addAll(s.ids(ToolRegistry.GET_TRANSACTION_DETAILS));
            contradictions.add("Known trusted device " + ref(s, ToolRegistry.GET_DEVICE_SIGNALS) + " but location "
                    + String.format("%,d", loc.distanceKm()) + " km from recent trusted activity " + ref(s, ToolRegistry.COMPARE_RECENT_LOCATIONS));
            c = Classification.SUSPICIOUS_TRANSACTION;
            risk = RiskLevel.MEDIUM;
            confidence = 0.50;
            requests.add("Request device session logs to resolve the device and location contradiction");
            finding = "Device and location signals contradict each other: the device is known and trusted "
                    + ref(s, ToolRegistry.GET_DEVICE_SIGNALS) + " while the location is anomalous "
                    + ref(s, ToolRegistry.COMPARE_RECENT_LOCATIONS) + ". Confidence is reduced and manual review is recommended.";
        } else {
            List<String> highs = new ArrayList<>();
            if (newDevice) {
                highs.add("new device");
                cited.addAll(s.ids(ToolRegistry.GET_DEVICE_SIGNALS));
            }
            if (locationHigh) {
                highs.add("location anomaly");
                cited.addAll(s.ids(ToolRegistry.COMPARE_RECENT_LOCATIONS));
            }
            if (authFailed) {
                highs.add("failed authentication");
            }
            if (blocklisted) {
                highs.add("merchant blocklist match");
            }
            cited.addAll(s.ids(ToolRegistry.GET_TRANSACTION_DETAILS));
            cited.addAll(s.ids(ToolRegistry.GET_MERCHANT_RISK_PROFILE));
            if (highs.size() >= 2) {
                ruleIds.add("R-03");
                ruleFacts.add("R-03: independentHighAnomalies=" + highs + ", customerDeniesMerchant=" + f.customerDeniesMerchant());
                c = f.customerDeniesMerchant() ? Classification.UNAUTHORISED_TRANSACTION : Classification.SUSPICIOUS_TRANSACTION;
                risk = RiskLevel.HIGH;
                confidence = Math.max(0.6, 0.90 - 0.04 * missingInformation.size());
                finding = (f.customerDeniesMerchant() ? "The customer denies the merchant " + ref(s, INTAKE) + ", the" : "The")
                        + " payment originated from " + (newDevice ? "a newly observed device " + ref(s, ToolRegistry.GET_DEVICE_SIGNALS) : "a flagged session")
                        + (locationHigh ? ", and the location differs materially from recent trusted activity " + ref(s, ToolRegistry.COMPARE_RECENT_LOCATIONS) : "")
                        + ". " + (blocklisted ? "The merchant is blocklisted " : "The merchant is not blocklisted ")
                        + ref(s, ToolRegistry.GET_MERCHANT_RISK_PROFILE) + ", so the assistant recommends investigator review rather than an automated outcome.";
            } else if (highs.size() == 1) {
                ruleIds.add("R-09");
                ruleFacts.add("R-09: independentHighAnomalies=" + highs);
                c = Classification.SUSPICIOUS_TRANSACTION;
                risk = RiskLevel.MEDIUM;
                confidence = 0.65;
                finding = "One high-severity indicator (" + highs.getFirst() + ") is present without independent corroboration; "
                        + "investigator review is recommended.";
            } else if (authSuccess && deviceOk && Boolean.TRUE.equals(device.deviceKnown()) && locationNone) {
                ruleIds.add("R-07");
                ruleFacts.add("R-07: authOutcome=SUCCESS, deviceKnown=true, location.anomalyLevel=" + loc.anomalyLevel()
                        + (disputes != null && disputes.succeeded() ? ", similarPriorClaims=" + disputes.similarCount() : ""));
                cited.addAll(s.ids(ToolRegistry.GET_DEVICE_SIGNALS));
                cited.addAll(s.ids(ToolRegistry.COMPARE_RECENT_LOCATIONS));
                cited.addAll(s.ids(ToolRegistry.GET_PRIOR_DISPUTES));
                c = Classification.GENUINE_TRANSACTION;
                risk = RiskLevel.LOW;
                confidence = 0.75;
                requests.add("Ask the reviewer to confirm with the customer through approved channels whether the purchase was made by a household member");
                finding = "Authentication succeeded " + ref(s, ToolRegistry.GET_TRANSACTION_DETAILS) + " on a known trusted device "
                        + ref(s, ToolRegistry.GET_DEVICE_SIGNALS) + " at a location consistent with recent activity "
                        + ref(s, ToolRegistry.COMPARE_RECENT_LOCATIONS) + "; the evidence supports customer-authorised activity, subject to reviewer confirmation.";
            } else {
                ruleIds.add("R-08");
                ruleFacts.add("R-08: no conclusive rule; highAnomalies=0, authOutcome=" + txn.authOutcome());
                c = Classification.INSUFFICIENT_EVIDENCE;
                risk = RiskLevel.MEDIUM;
                confidence = 0.40;
                finding = "No conclusive pattern was found in the available evidence; manual review is recommended.";
            }
        }

        if (txnOk && txn.authUnavailable()) {
            requests.add("Request the authentication challenge outcome from the card-processing platform");
        }
        Route route = DecisionModel.defaultRoute(c);
        if (ruleIds.contains("R-06")) {
            route = Route.MANUAL_REVIEW;
        }
        List<RetrievedPassage> passages = s.policy() == null ? List.of() : s.policy().passages();
        String policyRef;
        if (passages.isEmpty()) {
            ruleIds.add("R-05");
            ruleFacts.add("R-05: retrieval returned 0 passages at minScore=" + (s.policy() == null ? "n/a" : s.policy().threshold()));
            route = Route.MANUAL_REVIEW;
            confidence = Math.min(confidence, 0.50);
            policyRef = " No approved guidance met the retrieval threshold, so no procedural claim is made and human interpretation is required.";
        } else {
            policyRef = " Grounding: " + String.join(", ", passages.stream().limit(3)
                    .map(p -> p.documentId() + " v" + p.version() + " §" + p.section()).toList()) + ".";
        }
        String rationale = finding + policyRef + " Route for human review; no automated action taken.";
        return new RuleOutcome(c, risk, route, Math.round(confidence * 100d) / 100d, ruleIds, ruleFacts, contradictions,
                List.copyOf(requests), List.copyOf(cited), rationale);
    }

    /** Maps an outcome to the model output contract (rule IDs are never emitted by the model). */
    public static RawRecommendation toRaw(CaseFacts f, RuleOutcome o, PolicyContext policy, List<String> slaEvidenceIds,
                                          CaseSla sla) {
        List<String> evidenceIds = new ArrayList<>(o.evidenceIds());
        evidenceIds.addAll(slaEvidenceIds);
        return new RawRecommendation(f.caseId(), o.classification().name(), o.risk().name(), o.route().name(),
                o.confidence(), true, evidenceIds,
                policy.passages().stream().limit(3).map(p -> new RawRecommendation.RawCitation(p.documentId(), p.version(),
                        p.section(), p.chunkId(), p.score())).toList(),
                List.of(), o.contradictions(), o.evidenceRequests(), List.of(), List.of(), List.of(),
                o.rationale() + (sla != null && sla.succeeded() ? " Training SLA: " + sla.queue() + " " + sla.priority() + "." : ""));
    }

    public static PolicyContext toContext(PolicyEvidence p) {
        return new PolicyContext(p.query(), p.topK(), p.minScore(), p.passages());
    }

    private static String ref(EvidenceSnapshot s, String source) {
        List<String> ids = s.ids(source);
        return ids.isEmpty() ? "" : "[" + String.join(", ", ids) + "]";
    }

    private static String describe(CaseFacts f) {
        return switch (f.issueType()) {
            case GOODS_NOT_RECEIVED -> "fulfilment (goods not received)";
            case CANCELLED_NOT_REFUNDED -> "a cancellation that was not refunded";
            case INCORRECT_AMOUNT -> "the charged amount";
            default -> "the transaction";
        };
    }
}
