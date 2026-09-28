package com.bankms.util;

/** Regexes shared by request DTOs so every endpoint validates identity fields the same way. */
public final class ValidationPatterns {

    /** 5 letters, 4 digits, 1 letter (Indian PAN). */
    public static final String PAN = "^[A-Z]{5}[0-9]{4}[A-Z]$";
    /** 12 digits, never starting with 0 or 1 (Aadhaar-style). */
    public static final String AADHAAR = "^[2-9][0-9]{11}$";
    /** 10-digit Indian mobile number. */
    public static final String PHONE = "^[6-9][0-9]{9}$";
    public static final String PINCODE = "^[1-9][0-9]{5}$";
    /** 4-letter bank code, a zero, then a 6-character branch code. */
    public static final String IFSC = "^[A-Z]{4}0[A-Z0-9]{6}$";
    public static final String ACCOUNT_NUMBER = "^[0-9]{9,18}$";
    public static final String BRANCH_CODE = "^[0-9]{6}$";
    /** 8-64 chars with upper, lower, digit and symbol. */
    public static final String STRONG_PASSWORD = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,64}$";
    public static final String STRONG_PASSWORD_MESSAGE =
            "must be 8-64 characters with an uppercase letter, a lowercase letter, a digit and a symbol";
    public static final String IDEMPOTENCY_KEY = "^[A-Za-z0-9_-]{8,80}$";

    private ValidationPatterns() {
    }
}
