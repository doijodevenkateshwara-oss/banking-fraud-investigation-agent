package com.bfs.fraudcase.tools;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Allow-list of approved, read-only capabilities (guide section 2.5). Anything that could block a card, refund,
 * credit, move funds, file a report or contact a customer is forbidden and fails start-up if registered.
 *
 * @author Venkateshwara Doijode
 */
@Component
public class ToolRegistry {

    public record ToolDescriptor(String name, String purpose, boolean readOnly, boolean modelVisible) {
    }

    public static final String GET_TRANSACTION_DETAILS = "getTransactionDetails";
    public static final String GET_DEVICE_SIGNALS = "getDeviceSignals";
    public static final String COMPARE_RECENT_LOCATIONS = "compareRecentLocations";
    public static final String GET_MERCHANT_RISK_PROFILE = "getMerchantRiskProfile";
    public static final String GET_PRIOR_DISPUTES = "getPriorDisputes";
    public static final String CALCULATE_CASE_SLA = "calculateCaseSla";
    public static final String RETRIEVE_POLICY_EVIDENCE = "retrievePolicyEvidence";
    public static final String SAVE_AUDIT_EVENT = "saveAuditEvent";

    private static final List<ToolDescriptor> APPROVED = List.of(
            new ToolDescriptor(GET_TRANSACTION_DETAILS, "Retrieve transaction state, channel and authentication fields.", true, true),
            new ToolDescriptor(GET_DEVICE_SIGNALS, "Check device novelty and trust indicators.", true, true),
            new ToolDescriptor(COMPARE_RECENT_LOCATIONS, "Compare current and recent trusted locations.", true, true),
            new ToolDescriptor(GET_MERCHANT_RISK_PROFILE, "Return training merchant characteristics.", true, true),
            new ToolDescriptor(GET_PRIOR_DISPUTES, "Summarise earlier claims for the masked customer.", true, true),
            new ToolDescriptor(CALCULATE_CASE_SLA, "Calculate a training target for the proposed route.", true, true),
            new ToolDescriptor(RETRIEVE_POLICY_EVIDENCE, "Search approved policy chunks.", true, true),
            new ToolDescriptor(SAVE_AUDIT_EVENT, "Append an important event (application-internal, never model-visible).", false, false));

    /** Substrings that identify forbidden autonomous actions. */
    public static final Set<String> FORBIDDEN_KEYWORDS = Set.of(
            "block", "freeze", "refund", "credit", "transfer", "movefunds", "payout", "reverse", "chargeback",
            "filereport", "filesar", "fileclaim", "report", "submit", "contact", "email", "sms", "notify", "call", "send", "close");

    private final Map<String, ToolDescriptor> byName = APPROVED.stream()
            .collect(Collectors.toUnmodifiableMap(ToolDescriptor::name, Function.identity()));

    public List<ToolDescriptor> approved() {
        return APPROVED;
    }

    public boolean isModelVisible(String name) {
        ToolDescriptor d = byName.get(name);
        return d != null && d.modelVisible() && d.readOnly();
    }

    public Set<String> modelVisibleNames() {
        return APPROVED.stream().filter(d -> d.modelVisible() && d.readOnly()).map(ToolDescriptor::name)
                .collect(Collectors.toUnmodifiableSet());
    }

    public static boolean isForbiddenName(String toolName) {
        String n = toolName.toLowerCase(Locale.ROOT).replace("_", "");
        return FORBIDDEN_KEYWORDS.stream().anyMatch(n::contains);
    }

    public void assertNoForbiddenCapabilities() {
        assertAllowed(modelVisibleNames());
    }

    /** Verifies an adapter's exposed tool names are all approved and none is forbidden. */
    public void assertAllowed(Collection<String> exposedToolNames) {
        for (String name : exposedToolNames) {
            if (!isModelVisible(name) || isForbiddenName(name)) {
                throw new IllegalStateException("Tool not permitted by the safety boundary: " + name);
            }
        }
    }
}
