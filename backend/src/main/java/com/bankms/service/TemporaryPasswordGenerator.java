package com.bankms.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/** Random passwords that satisfy the strong-password rule, for staff-created users. */
@Component
public class TemporaryPasswordGenerator {

    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijkmnopqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final String SYMBOLS = "@#$%&*!";
    private static final String ALL = UPPER + LOWER + DIGITS + SYMBOLS;

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        char[] password = new char[12];
        password[0] = pick(UPPER);
        password[1] = pick(LOWER);
        password[2] = pick(DIGITS);
        password[3] = pick(SYMBOLS);
        for (int i = 4; i < password.length; i++) {
            password[i] = pick(ALL);
        }
        for (int i = password.length - 1; i > 0; i--) { // Fisher-Yates so the classes are not in fixed slots
            int j = random.nextInt(i + 1);
            char tmp = password[i];
            password[i] = password[j];
            password[j] = tmp;
        }
        return new String(password);
    }

    private char pick(String alphabet) {
        return alphabet.charAt(random.nextInt(alphabet.length()));
    }
}
