package com.bankms.service.calc;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InterestCalculatorTest {

    private final InterestCalculator calculator = new InterestCalculator();
    private final YearMonth september = YearMonth.of(2026, 9); // 30 days
    private final BigDecimal rate = new BigDecimal("3.65");     // 0.01% per day: easy mental maths

    @Test
    void constantBalanceEarnsOneMonthOfInterest() {
        InterestCalculator.Result result =
                calculator.monthlyInterest(new BigDecimal("100000.00"), List.of(), september, rate);
        // 100000 * 30 days * 3.65% / 365 = 300.00
        assertThat(result.interest()).isEqualByComparingTo("300.00");
        assertThat(result.averageBalance()).isEqualByComparingTo("100000.00");
    }

    @Test
    void depositMidMonthOnlyEarnsFromTheDayItArrives() {
        List<InterestCalculator.BalancePoint> movements = List.of(
                new InterestCalculator.BalancePoint(LocalDate.of(2026, 9, 21), new BigDecimal("100000.00")));
        InterestCalculator.Result result = calculator.monthlyInterest(BigDecimal.ZERO, movements, september, rate);
        // in the account for 21-30 September = 10 days -> 100000 * 10 * 0.0001 = 100.00
        assertThat(result.interest()).isEqualByComparingTo("100.00");
    }

    @Test
    void usesTheLastBalanceOfEachDay() {
        List<InterestCalculator.BalancePoint> movements = List.of(
                new InterestCalculator.BalancePoint(LocalDate.of(2026, 9, 1), new BigDecimal("500000.00")),
                new InterestCalculator.BalancePoint(LocalDate.of(2026, 9, 1), new BigDecimal("10000.00")));
        InterestCalculator.Result result = calculator.monthlyInterest(BigDecimal.ZERO, movements, september, rate);
        // an intraday spike does not earn interest: 10000 * 30 * 0.0001 = 30.00
        assertThat(result.interest()).isEqualByComparingTo("30.00");
    }

    @Test
    void emptyAccountEarnsNothing() {
        assertThat(calculator.monthlyInterest(BigDecimal.ZERO, List.of(), september, rate).interest())
                .isEqualByComparingTo("0");
    }
}
