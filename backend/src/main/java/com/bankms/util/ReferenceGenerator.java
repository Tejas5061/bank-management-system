package com.bankms.util;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Transaction reference numbers like TXN260928K7Q2M9XA3B: prefix, business date, then 10 random
 * characters from a 32-symbol alphabet without look-alikes (no I/O/0/1), about 1.1e15 combinations
 * per day. The database unique key is the final guarantee.
 */
@Component
public class ReferenceGenerator {

    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyMMdd");

    private final SecureRandom random = new SecureRandom();
    private final Clock clock;

    public ReferenceGenerator(Clock clock) {
        this.clock = clock;
    }

    public String next() {
        StringBuilder sb = new StringBuilder("TXN").append(LocalDate.now(clock).format(DATE));
        for (int i = 0; i < 10; i++) {
            sb.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }
}
