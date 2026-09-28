package com.bankms.util;

/** Masks identity documents and account numbers before they leave the API. */
public final class Masking {

    private Masking() {
    }

    /** ABCPS1234K becomes XXXXXX234K. */
    public static String pan(String pan) {
        return keepLast(pan, 4, 'X');
    }

    /** 234567891234 becomes XXXX XXXX 1234. */
    public static String aadhaar(String aadhaar) {
        if (aadhaar == null || aadhaar.length() < 4) {
            return aadhaar;
        }
        return "XXXX XXXX " + aadhaar.substring(aadhaar.length() - 4);
    }

    /** 000110000017 becomes XXXXXXXX0017. */
    public static String accountNumber(String accountNumber) {
        return keepLast(accountNumber, 4, 'X');
    }

    private static String keepLast(String value, int visible, char mask) {
        if (value == null || value.length() <= visible) {
            return value;
        }
        int hidden = value.length() - visible;
        return String.valueOf(mask).repeat(hidden) + value.substring(hidden);
    }
}
