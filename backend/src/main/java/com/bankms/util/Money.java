package com.bankms.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Money is always BigDecimal with scale 2. {@code double} is never used for amounts: 0.1 + 0.2 is
 * 0.30000000000000004 in binary floating point, and ledgers must add up to the paisa.
 */
public final class Money {

    public static final int SCALE = 2;
    /** Banker's rounding for computed amounts (interest), so rounding errors do not drift one way. */
    public static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE);

    private Money() {
    }

    /** Normalises an already-validated input amount; refuses to silently drop fractional paise. */
    public static BigDecimal normalize(BigDecimal amount) {
        return amount.setScale(SCALE, RoundingMode.UNNECESSARY);
    }

    public static BigDecimal round(BigDecimal amount) {
        return amount.setScale(SCALE, ROUNDING);
    }

    /** "₹12,34,567.50" - for notifications and messages. */
    public static String format(BigDecimal amount) {
        String grouped = group(amount);
        return grouped.startsWith("-") ? "-₹" + grouped.substring(1) : "₹" + grouped;
    }

    /**
     * Indian digit grouping (lakh/crore): the last three digits, then pairs - 1,23,45,678.90.
     * java.text cannot do this: DecimalFormat supports only one grouping size, so even the en-IN
     * locale prints 1,234,567.50.
     */
    public static String group(BigDecimal amount) {
        String plain = amount.setScale(SCALE, ROUNDING).abs().toPlainString();
        int dot = plain.indexOf('.');
        String integer = plain.substring(0, dot);
        StringBuilder out = new StringBuilder();
        int length = integer.length();
        for (int i = 0; i < length; i++) {
            int remaining = length - i;
            if (i > 0 && (remaining == 3 || (remaining > 3 && (remaining - 3) % 2 == 0))) {
                out.append(',');
            }
            out.append(integer.charAt(i));
        }
        return (amount.signum() < 0 ? "-" : "") + out + plain.substring(dot);
    }
}
