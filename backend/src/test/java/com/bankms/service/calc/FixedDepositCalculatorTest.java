package com.bankms.service.calc;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class FixedDepositCalculatorTest {

    private final FixedDepositCalculator calculator = new FixedDepositCalculator();

    @ParameterizedTest(name = "{0} at {1}% for {2} months -> {3}")
    @CsvSource({
            "100000, 6.75, 3,  101687.50",   // one quarter: P * (1 + 6.75/400)
            "100000, 6.75, 12, 106922.79",   // four quarters, compounded
            "100000, 6.75, 7,  103985.12",   // two quarters + one month of simple interest
            "50000,  6.75, 12, 53461.39"
    })
    void compoundsQuarterlyWithSimpleInterestForBrokenMonths(String principal, String rate, int months, String expected) {
        assertThat(calculator.maturityAmount(new BigDecimal(principal), new BigDecimal(rate), months))
                .isEqualByComparingTo(expected);
    }
}
