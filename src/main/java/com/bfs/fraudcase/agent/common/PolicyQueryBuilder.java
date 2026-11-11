package com.bfs.fraudcase.agent.common;

import com.bfs.fraudcase.domain.CardPossession;
import com.bfs.fraudcase.domain.CaseFacts;
import com.bfs.fraudcase.domain.IssueType;
import com.bfs.fraudcase.domain.PaymentChannel;
import com.bfs.fraudcase.tools.dto.DeviceSignals;
import com.bfs.fraudcase.tools.dto.LocationComparison;
import com.bfs.fraudcase.tools.dto.TransactionDetails;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the retrieval query from case type, evidence indicators and the investigation goal (guide section 6.6).
 *
 * @author Venkateshwara Doijode
 */
public final class PolicyQueryBuilder {

    private PolicyQueryBuilder() {
    }

    public static String build(CaseFacts f, TransactionDetails txn, DeviceSignals device, LocationComparison location) {
        List<String> parts = new ArrayList<>();
        boolean cnp = f.paymentChannel() == PaymentChannel.CARD_NOT_PRESENT;
        switch (f.issueType()) {
            case MERCHANT_NOT_RECOGNISED -> parts.add("customer denies " + (cnp ? "card-not-present transaction" : "card transaction"));
            case GOODS_NOT_RECEIVED -> parts.add("merchant recognised dispute about fulfilment goods not received");
            case CANCELLED_NOT_REFUNDED -> parts.add("merchant recognised dispute cancellation refund not received");
            case INCORRECT_AMOUNT -> parts.add("merchant recognised dispute incorrect amount");
            case DUPLICATE_CHARGE -> parts.add("duplicate posting same payment fingerprint");
            case OTHER -> parts.add(f.customerStatement() == null ? "payment query"
                    : PolicyQuerySanitiser.clip(f.customerStatement(), 200));
        }
        if (f.issueType() == IssueType.MERCHANT_NOT_RECOGNISED && f.cardPossession() == CardPossession.IN_POSSESSION) {
            parts.add("retains card");
        }
        if (txn != null && (!txn.succeeded() || !txn.found())) {
            parts.add("transaction record not found");
            parts.add("required evidence unavailable");
        }
        if (txn != null && txn.duplicateFingerprintMatch() && f.issueType() != IssueType.DUPLICATE_CHARGE) {
            parts.add("duplicate posting");
        }
        boolean deviceOk = device != null && device.succeeded();
        boolean locOk = location != null && location.succeeded();
        boolean newDevice = deviceOk && Boolean.FALSE.equals(device.deviceKnown());
        boolean trustedDevice = deviceOk && Boolean.TRUE.equals(device.deviceKnown());
        boolean locationAnomaly = locOk && "HIGH".equals(location.anomalyLevel());
        if (trustedDevice && locationAnomaly) {
            parts.add("known trusted device used from an anomalous location");
            parts.add("contradictory device and location signals");
        } else {
            if (newDevice) {
                parts.add("newly observed device");
            }
            if (locationAnomaly) {
                parts.add("location inconsistent with recent trusted activity");
            }
            if (newDevice && locationAnomaly) {
                parts.add("independent high-severity indicators");
            }
            if (trustedDevice && locOk && !locationAnomaly && txn != null && "SUCCESS".equals(txn.authOutcome())
                    && f.issueType() == IssueType.MERCHANT_NOT_RECOGNISED) {
                parts.add("authentication succeeded on known trusted device");
                parts.add("location consistent");
                parts.add("customer-authorised activity");
            }
        }
        if (device != null && !deviceOk && location != null && !locOk) {
            parts.add("device and location evidence unavailable");
        }
        return String.join(" · ", parts);
    }

    /** Small helper kept separate so statement text is always clipped and stripped of markup. */
    static final class PolicyQuerySanitiser {
        static String clip(String s, int max) {
            String t = s.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();
            return t.length() > max ? t.substring(0, max) : t;
        }
    }
}
