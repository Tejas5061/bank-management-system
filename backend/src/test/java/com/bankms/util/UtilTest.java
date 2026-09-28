package com.bankms.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UtilTest {

    @Test
    void luhnCatchesSingleDigitTyposAndAdjacentSwaps() {
        String body = "00011000001";
        String number = body + Luhn.checkDigit(body);
        assertThat(Luhn.isValid(number)).isTrue();
        assertThat(Luhn.isValid(number.substring(0, 5) + "9" + number.substring(6))).isFalse();
        String swapped = number.substring(0, 4) + number.charAt(5) + number.charAt(4) + number.substring(6);
        assertThat(Luhn.isValid(swapped)).isFalse();
        assertThat(Luhn.isValid("12ab")).isFalse();
    }

    @Test
    void masksIdentityDocumentsAndAccountNumbers() {
        assertThat(Masking.pan("ABKPS4821M")).isEqualTo("XXXXXX821M");
        assertThat(Masking.aadhaar("482915736024")).isEqualTo("XXXX XXXX 6024");
        assertThat(Masking.accountNumber("000110000015")).isEqualTo("XXXXXXXX0015");
        assertThat(Masking.pan(null)).isNull();
    }

    @Test
    void moneyRefusesToSilentlyDropFractionalPaise() {
        assertThat(Money.normalize(new BigDecimal("10.5")).scale()).isEqualTo(2);
        assertThatThrownBy(() -> Money.normalize(new BigDecimal("10.555"))).isInstanceOf(ArithmeticException.class);
    }

    @Test
    void formatsRupeesWithIndianGrouping() {
        assertThat(Money.format(new BigDecimal("1234567.5"))).isEqualTo("₹12,34,567.50");
        assertThat(Money.format(new BigDecimal("100000"))).isEqualTo("₹1,00,000.00");
        assertThat(Money.format(new BigDecimal("999.9"))).isEqualTo("₹999.90");
        assertThat(Money.format(new BigDecimal("-2500"))).isEqualTo("-₹2,500.00");
    }
}
