package com.bankms.util;

/**
 * Luhn (mod 10) check digit, as used on card numbers. Appended to generated account numbers so
 * a single mistyped digit or a swap of two adjacent digits is caught before any lookup.
 */
public final class Luhn {

    private Luhn() {
    }

    public static int checkDigit(String digits) {
        int sum = 0;
        boolean doubleIt = true;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = Character.digit(digits.charAt(i), 10);
            if (d < 0) {
                throw new IllegalArgumentException("Not a digit string: " + digits);
            }
            if (doubleIt) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
            doubleIt = !doubleIt;
        }
        return (10 - (sum % 10)) % 10;
    }

    public static boolean isValid(String number) {
        if (number == null || number.length() < 2 || !number.chars().allMatch(Character::isDigit)) {
            return false;
        }
        String body = number.substring(0, number.length() - 1);
        return checkDigit(body) == Character.digit(number.charAt(number.length() - 1), 10);
    }
}
