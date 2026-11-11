package com.bfs.fraudcase.agent.langchain4j;

import com.bfs.fraudcase.domain.Json;
import com.bfs.fraudcase.tools.EvidenceTools;
import com.bfs.fraudcase.tools.InvestigationSession;
import com.bfs.fraudcase.tools.ToolRegistry;
import com.bfs.fraudcase.tools.dto.ToolResult;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * LangChain4j {@code @Tool} registration of the approved read-only evidence tools. Each method delegates to the
 * framework-neutral {@link EvidenceTools} and returns the structured result plus its trace and evidence IDs.
 * Method names must match {@link ToolRegistry}; this is verified at start-up.
 *
 * @author Venkateshwara Doijode
 */
public class Lc4jEvidenceTools {

    private final EvidenceTools tools;

    public Lc4jEvidenceTools(EvidenceTools tools) {
        this.tools = tools;
    }

    @Tool("Retrieve transaction state, channel, amount, authentication outcome and duplicate fingerprint for a transaction reference.")
    public String getTransactionDetails(@P("transaction reference, format TXN-YYYY-NNNNNN") String transactionRef) {
        return wrap(ToolRegistry.GET_TRANSACTION_DETAILS, tools.getTransactionDetails(transactionRef));
    }

    @Tool("Check device novelty and trust indicators for the disputed transaction.")
    public String getDeviceSignals(@P("masked customer reference, e.g. CUST-••••-4821") String maskedCustomerRef,
                                   @P("transaction reference") String transactionRef) {
        return wrap(ToolRegistry.GET_DEVICE_SIGNALS, tools.getDeviceSignals(maskedCustomerRef, transactionRef));
    }

    @Tool("Compare the transaction location with recent trusted customer activity.")
    public String compareRecentLocations(@P("masked customer reference") String maskedCustomerRef,
                                         @P("transaction reference") String transactionRef) {
        return wrap(ToolRegistry.COMPARE_RECENT_LOCATIONS, tools.compareRecentLocations(maskedCustomerRef, transactionRef));
    }

    @Tool("Return the training merchant risk band, blocklist match and category for a masked merchant.")
    public String getMerchantRiskProfile(@P("masked merchant text, e.g. NORTHSTAR DIGITAL ••••73") String maskedMerchant) {
        return wrap(ToolRegistry.GET_MERCHANT_RISK_PROFILE, tools.getMerchantRiskProfile(maskedMerchant));
    }

    @Tool("Summarise earlier dispute claims for the masked customer over the last 180 days.")
    public String getPriorDisputes(@P("masked customer reference") String maskedCustomerRef) {
        return wrap(ToolRegistry.GET_PRIOR_DISPUTES, tools.getPriorDisputes(maskedCustomerRef));
    }

    @Tool("Calculate the training queue, priority and SLA target for a proposed route and risk level.")
    public String calculateCaseSla(@P("FRAUD_INVESTIGATION, DISPUTE_OPERATIONS, PAYMENT_OPERATIONS or MANUAL_REVIEW") String route,
                                   @P("LOW, MEDIUM or HIGH") String riskLevel) {
        return wrap(ToolRegistry.CALCULATE_CASE_SLA, tools.calculateCaseSla(route, riskLevel));
    }

    @Tool("Search approved policy passages. Returns documentId, version, section, chunkId, score and passage, or abstained=true when no passage meets the threshold.")
    public String retrievePolicyEvidence(@P("search query built from case type and evidence indicators") String query) {
        return wrap(ToolRegistry.RETRIEVE_POLICY_EVIDENCE, tools.retrievePolicyEvidence(query));
    }

    private String wrap(String toolName, ToolResult result) {
        InvestigationSession s = tools.session();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("traceId", s.traceId(toolName).orElse(null));
        out.put("status", result.status());
        out.put("evidenceIds", s.evidenceIds(toolName));
        out.put("result", result);
        return Json.write(out);
    }
}
