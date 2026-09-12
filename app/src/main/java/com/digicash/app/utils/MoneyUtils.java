package com.digicash.app.utils;

import java.util.regex.Pattern;

/**
 * Safe conversion helpers between rupee-denominated user input/display
 * strings and long integer paise (minor currency units). No float or
 * double is used anywhere in this class - all arithmetic is on long
 * integers or string manipulation, to avoid floating-point rounding
 * errors in financial calculations.
 */
public final class MoneyUtils {

    private MoneyUtils() {
        // Constants/utility holder only.
    }

    /** Maximum number of digits allowed in the rupees portion of user input, to bound overflow. */
    private static final int MAX_RUPEE_DIGITS = 9;

    /**
     * Accepts: "0", "10", "10.5", "10.50" - rejects negative signs,
     * more than 2 decimal digits, leading zeros other than a bare "0",
     * empty strings, and non-numeric characters.
     */
    private static final Pattern RUPEES_INPUT_PATTERN = Pattern.compile(
            "^(0|[1-9]\\d{0," + (MAX_RUPEE_DIGITS - 1) + "})(\\.\\d{1,2})?$");

    /**
     * Safely parses a user-entered rupee amount string (e.g. "10.50")
     * into long paise (e.g. 1050L). Never uses float/double parsing.
     *
     * @throws IllegalArgumentException if the input is null, blank, or
     *         does not match a valid non-negative rupees-and-paise format.
     */
    public static long rupeesStringToPaise(String rupeesInput) {
        if (rupeesInput == null) {
            throw new IllegalArgumentException("Amount must not be null");
        }
        String trimmed = rupeesInput.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Amount must not be empty");
        }
        if (!RUPEES_INPUT_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("Enter a valid amount (e.g. 10 or 10.50)");
        }

        String rupeesPart;
        String paisePart;
        int dotIndex = trimmed.indexOf('.');
        if (dotIndex == -1) {
            rupeesPart = trimmed;
            paisePart = "00";
        } else {
            rupeesPart = trimmed.substring(0, dotIndex);
            String rawPaise = trimmed.substring(dotIndex + 1);
            paisePart = rawPaise.length() == 1 ? rawPaise + "0" : rawPaise;
        }

        long rupees;
        long paise;
        try {
            rupees = Long.parseLong(rupeesPart);
            paise = Long.parseLong(paisePart);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Enter a valid amount (e.g. 10 or 10.50)", e);
        }

        long total = rupees * 100L + paise;
        if (total <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        return total;
    }

    /**
     * Formats long paise as a rupee display string with the ₹ symbol,
     * e.g. 1050L -> "₹10.50". Handles zero and large values correctly
     * using only integer arithmetic.
     */
    public static String formatPaiseToRupees(long paiseAmount) {
        boolean negative = paiseAmount < 0;
        long absolute = Math.abs(paiseAmount);
        long rupees = absolute / 100L;
        long paise = absolute % 100L;
        String formatted = "\u20B9" + rupees + "." + (paise < 10 ? "0" + paise : String.valueOf(paise));
        return negative ? "-" + formatted : formatted;
    }
}
