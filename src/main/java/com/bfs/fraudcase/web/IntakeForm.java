package com.bfs.fraudcase.web;

import com.bfs.fraudcase.application.CaseIntakeCommand;
import com.bfs.fraudcase.domain.CardPossession;
import com.bfs.fraudcase.domain.IssueType;
import com.bfs.fraudcase.domain.PaymentChannel;
import com.bfs.fraudcase.domain.ReportedChannel;
import com.bfs.fraudcase.domain.TransactionStatus;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mutable form backing the intake screen; raw strings are kept so invalid input can be re-displayed.
 *
 * @author Venkateshwara Doijode
 */
public class IntakeForm implements Serializable {
    private String maskedCustomerRef = "";
    private String transactionRef = "";
    private String reportedChannel = "PHONE";
    private String amount = "";
    private String currency = "INR";
    private String occurredAt = "";
    private String utcOffset = "+05:30";
    private String transactionStatus = "POSTED";
    private String paymentChannel = "CARD_NOT_PRESENT";
    private String maskedMerchant = "";
    private String homeRegion = "";
    private String customerLocation = "";
    private String issueType = "MERCHANT_NOT_RECOGNISED";
    private String customerStatement = "";
    private String cardPossession = "IN_POSSESSION";
    private boolean merchantRecognised;
    private boolean syntheticDataConfirmed;
    private boolean reviewRequired = true;

    /** Converts to a command, collecting conversion errors; semantic validation happens in the application layer. */
    public CaseIntakeCommand toCommand(Map<String, String> errors) {
        BigDecimal amt = null;
        try {
            amt = amount.isBlank() ? null : new BigDecimal(amount.replace(",", "").trim());
        } catch (NumberFormatException e) {
            errors.put("amount", "Enter a number such as 48750.00");
        }
        java.time.OffsetDateTime at = null;
        try {
            at = occurredAt.isBlank() ? null : LocalDateTime.parse(occurredAt).atOffset(ZoneOffset.of(utcOffset));
        } catch (RuntimeException e) {
            errors.put("occurredAt", "Enter a valid date and time");
        }
        return new CaseIntakeCommand(maskedCustomerRef.strip(), homeRegion, customerLocation,
                parse(ReportedChannel.class, reportedChannel, "reportedChannel", errors), transactionRef.strip(), amt,
                currency.strip().toUpperCase(), at, parse(TransactionStatus.class, transactionStatus, "transactionStatus", errors),
                parse(PaymentChannel.class, paymentChannel, "paymentChannel", errors), maskedMerchant.strip(),
                parse(IssueType.class, issueType, "issueType", errors), customerStatement,
                parse(CardPossession.class, cardPossession, "cardPossession", errors), merchantRecognised,
                syntheticDataConfirmed, reviewRequired);
    }

    /** Issue indicator chips (guide figure 1), derived from the entered values. */
    public Map<String, String> indicators() {
        Map<String, String> chips = new LinkedHashMap<>();
        if ("MERCHANT_NOT_RECOGNISED".equals(issueType) || !merchantRecognised) {
            chips.put("Merchant not recognised", "chip-danger");
        }
        if ("IN_POSSESSION".equals(cardPossession)) {
            chips.put("Card still present", "chip-ok");
        }
        if ("CARD_NOT_PRESENT".equals(paymentChannel)) {
            chips.put("Online purchase", "chip-info");
        }
        if (customerStatement.toLowerCase().contains("one-time password") || customerStatement.toLowerCase().contains("otp")) {
            chips.put("OTP outcome unknown", "chip-warn");
        }
        return chips;
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String v, String field, Map<String, String> errors) {
        try {
            return v == null || v.isBlank() ? null : Enum.valueOf(type, v);
        } catch (IllegalArgumentException e) {
            errors.put(field, "Select a valid option");
            return null;
        }
    }

    public static IntakeForm sample() {
        IntakeForm f = new IntakeForm();
        f.maskedCustomerRef = "CUST-••••-4821";
        f.transactionRef = "TXN-2026-784102";
        f.reportedChannel = "PHONE";
        f.amount = "48750.00";
        f.occurredAt = "2026-11-06T08:42";
        f.maskedMerchant = "NORTHSTAR DIGITAL ••••73";
        f.homeRegion = "Karnataka";
        f.customerLocation = "Bengaluru, Karnataka";
        f.customerStatement = "I do not recognise this online purchase. I still have my card and did not share the one-time password.";
        f.syntheticDataConfirmed = true;
        return f;
    }

    public String getMaskedCustomerRef() { return maskedCustomerRef; }
    public void setMaskedCustomerRef(String v) { maskedCustomerRef = v == null ? "" : v; }
    public String getTransactionRef() { return transactionRef; }
    public void setTransactionRef(String v) { transactionRef = v == null ? "" : v; }
    public String getReportedChannel() { return reportedChannel; }
    public void setReportedChannel(String v) { reportedChannel = v; }
    public String getAmount() { return amount; }
    public void setAmount(String v) { amount = v == null ? "" : v; }
    public String getCurrency() { return currency; }
    public void setCurrency(String v) { currency = v == null ? "" : v; }
    public String getOccurredAt() { return occurredAt; }
    public void setOccurredAt(String v) { occurredAt = v == null ? "" : v; }
    public String getUtcOffset() { return utcOffset; }
    public void setUtcOffset(String v) { utcOffset = v == null ? "+05:30" : v; }
    public String getTransactionStatus() { return transactionStatus; }
    public void setTransactionStatus(String v) { transactionStatus = v; }
    public String getPaymentChannel() { return paymentChannel; }
    public void setPaymentChannel(String v) { paymentChannel = v; }
    public String getMaskedMerchant() { return maskedMerchant; }
    public void setMaskedMerchant(String v) { maskedMerchant = v == null ? "" : v; }
    public String getHomeRegion() { return homeRegion; }
    public void setHomeRegion(String v) { homeRegion = v == null ? "" : v; }
    public String getCustomerLocation() { return customerLocation; }
    public void setCustomerLocation(String v) { customerLocation = v == null ? "" : v; }
    public String getIssueType() { return issueType; }
    public void setIssueType(String v) { issueType = v; }
    public String getCustomerStatement() { return customerStatement; }
    public void setCustomerStatement(String v) { customerStatement = v == null ? "" : v; }
    public String getCardPossession() { return cardPossession; }
    public void setCardPossession(String v) { cardPossession = v; }
    public boolean isMerchantRecognised() { return merchantRecognised; }
    public void setMerchantRecognised(boolean v) { merchantRecognised = v; }
    public boolean isSyntheticDataConfirmed() { return syntheticDataConfirmed; }
    public void setSyntheticDataConfirmed(boolean v) { syntheticDataConfirmed = v; }
    public boolean isReviewRequired() { return reviewRequired; }
    public void setReviewRequired(boolean v) { reviewRequired = v; }
}
