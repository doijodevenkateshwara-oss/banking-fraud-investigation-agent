package com.bfs.fraudcase.application;

import com.bfs.fraudcase.domain.Masking;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Field-level intake validation: mandatory fields, masking, enums, amount/currency and statement safety.
 *
 * @author Venkateshwara Doijode
 */
@Component
public class IntakeValidator {

    static final int MAX_STATEMENT = 1000;
    private static final Pattern REGION = Pattern.compile("^[\\p{L} ,.'-]{0,80}$");
    private final Clock clock;

    public IntakeValidator(Clock clock) {
        this.clock = clock;
    }

    public void validate(CaseIntakeCommand c) {
        Map<String, String> e = new LinkedHashMap<>();
        if (c == null) {
            throw new ValidationException("request", "Request body is required");
        }
        if (blank(c.maskedCustomerRef())) {
            e.put("maskedCustomerRef", "Masked customer reference is required");
        } else if (!Masking.MASKED_CUSTOMER_REF.matcher(c.maskedCustomerRef()).matches()) {
            e.put("maskedCustomerRef", "Use a masked reference such as CUST-••••-4821; full identifiers are not permitted");
        }
        if (blank(c.transactionRef())) {
            e.put("transactionRef", "Transaction reference is required");
        } else if (!Masking.TRANSACTION_REF.matcher(c.transactionRef()).matches()) {
            e.put("transactionRef", "Use the format TXN-YYYY-NNNNNN");
        }
        if (c.amount() == null) {
            e.put("amount", "Amount is required");
        } else if (c.amount().signum() <= 0) {
            e.put("amount", "Amount must be positive");
        } else if (c.amount().scale() > 2 || c.amount().compareTo(new BigDecimal("10000000000")) >= 0) {
            e.put("amount", "Amount must have at most 2 decimals and be below 10,000,000,000");
        }
        if (blank(c.currency())) {
            e.put("currency", "Currency is required");
        } else if (!isIsoCurrency(c.currency())) {
            e.put("currency", "Use an ISO 4217 currency code");
        }
        if (c.occurredAt() == null) {
            e.put("occurredAt", "Transaction timestamp is required");
        } else if (c.occurredAt().isAfter(OffsetDateTime.now(clock).plusMinutes(5))) {
            e.put("occurredAt", "Transaction timestamp cannot be in the future");
        }
        if (c.transactionStatus() == null) {
            e.put("transactionStatus", "Transaction status is required");
        }
        if (c.paymentChannel() == null) {
            e.put("paymentChannel", "Payment channel is required");
        }
        if (blank(c.maskedMerchant())) {
            e.put("maskedMerchant", "Masked merchant is required");
        } else if (!Masking.MASKED_MERCHANT.matcher(c.maskedMerchant()).matches()) {
            e.put("maskedMerchant", "Use masked merchant text such as NORTHSTAR DIGITAL ••••73");
        }
        if (c.issueType() == null) {
            e.put("issueType", "Issue type is required");
        }
        if (blank(c.customerStatement())) {
            e.put("customerStatement", "Customer statement is required");
        } else if (c.customerStatement().length() > MAX_STATEMENT) {
            e.put("customerStatement", "Statement must be at most " + MAX_STATEMENT + " characters");
        } else if (Masking.containsMarkup(c.customerStatement())) {
            e.put("customerStatement", "Markup or scripts are not permitted");
        } else if (Masking.containsPanLike(c.customerStatement()) || Masking.containsLongDigitRun(c.customerStatement())) {
            e.put("customerStatement", "Remove card, account or other long numbers from the statement");
        }
        checkFree(e, "homeRegion", c.homeRegion());
        checkFree(e, "customerLocation", c.customerLocation());
        if (!Boolean.TRUE.equals(c.syntheticDataConfirmed())) {
            e.put("syntheticDataConfirmed", "Confirm that only synthetic training data is used");
        }
        if (!Boolean.TRUE.equals(c.reviewRequired())) {
            e.put("reviewRequired", "Human review is mandatory and must be confirmed");
        }
        if (!e.isEmpty()) {
            throw new ValidationException(e);
        }
    }

    private static void checkFree(Map<String, String> e, String field, String value) {
        if (value != null && !value.isBlank() && !REGION.matcher(value).matches()) {
            e.put(field, "Letters, spaces and basic punctuation only (max 80)");
        }
    }

    private static boolean isIsoCurrency(String code) {
        try {
            // XXX (no currency), XTS (testing) and precious-metal codes are not valid payment currencies.
            return code.length() == 3 && !java.util.Set.of("XXX", "XTS", "XAU", "XAG", "XPT", "XPD").contains(code)
                    && Currency.getInstance(code).getCurrencyCode().equals(code);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
