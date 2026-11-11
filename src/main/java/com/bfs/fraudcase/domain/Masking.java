package com.bfs.fraudcase.domain;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Masking and sensitive-data detection helpers shared by intake validation, logging and tool summaries.
 *
 * @author Venkateshwara Doijode
 */
public final class Masking {

    public static final Pattern MASKED_CUSTOMER_REF = Pattern.compile("^CUST-[•*]{4}-\\d{4}$");
    public static final Pattern TRANSACTION_REF = Pattern.compile("^TXN-\\d{4}-\\d{6}$");
    public static final Pattern MASKED_MERCHANT = Pattern.compile("^[A-Z0-9 &'.-]{2,40} [•*]{2,6}\\d{2}$");
    private static final Pattern DIGIT_RUN = Pattern.compile("(?:\\d[ -]?){12,19}");
    private static final Pattern LONG_DIGITS = Pattern.compile("\\d{9,}");
    private static final Pattern MARKUP = Pattern.compile("<[^>]*>|javascript:|on\\w+\\s*=", Pattern.CASE_INSENSITIVE);

    private Masking() {
    }

    /** True when the text contains a 12-19 digit run that passes the Luhn check (likely a card number). */
    public static boolean containsPanLike(String text) {
        if (text == null) {
            return false;
        }
        Matcher m = DIGIT_RUN.matcher(text);
        while (m.find()) {
            String digits = m.group().replaceAll("[ -]", "");
            if (digits.length() >= 12 && digits.length() <= 19 && luhn(digits)) {
                return true;
            }
        }
        return false;
    }

    public static boolean containsLongDigitRun(String text) {
        return text != null && LONG_DIGITS.matcher(text.replaceAll("[ -]", "")).find();
    }

    public static boolean containsMarkup(String text) {
        return text != null && MARKUP.matcher(text).find();
    }

    /** Redacts long digit runs for logs and summaries. */
    public static String redact(String text) {
        if (text == null) {
            return null;
        }
        return DIGIT_RUN.matcher(text).replaceAll("[REDACTED]");
    }

    static boolean luhn(String digits) {
        int sum = 0;
        boolean alt = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int n = digits.charAt(i) - '0';
            if (alt) {
                n *= 2;
                if (n > 9) {
                    n -= 9;
                }
            }
            sum += n;
            alt = !alt;
        }
        return sum % 10 == 0;
    }
}
