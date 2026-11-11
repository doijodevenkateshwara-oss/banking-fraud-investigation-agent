package com.bfs.fraudcase.domain;

import java.util.List;
import java.util.Locale;

/**
 * Flags instruction-like text inside untrusted data (customer statements, policy passages). Detection never
 * changes routing: the text is still treated purely as data; the flag is surfaced to the reviewer and audited.
 *
 * @author Venkateshwara Doijode
 */
public final class PromptInjectionDetector {

    private static final List<String> MARKERS = List.of(
            "ignore previous", "ignore all previous", "ignore the above", "disregard previous", "system prompt",
            "you are now", "humanreviewrequired", "issue a refund", "issue refund", "approve the refund",
            "block the card", "transfer funds", "contact the customer", "file a report", "override the policy",
            "set confidence", "developer mode");

    private PromptInjectionDetector() {
    }

    public static boolean looksLikeInjection(String text) {
        if (text == null) {
            return false;
        }
        String t = text.toLowerCase(Locale.ROOT);
        return MARKERS.stream().anyMatch(t::contains);
    }
}
