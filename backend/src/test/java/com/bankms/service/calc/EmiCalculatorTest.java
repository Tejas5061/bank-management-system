package com.bankms.service.calc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmiCalculatorTest {

    private final EmiCalculator calculator = new EmiCalculator();

    @ParameterizedTest(name = "{0} at {1}% for {2} months -> {3}")
    @CsvSource({
            "500000, 10,    60,  10623.52",
            "200000, 11.50, 12,  17723.01",
            "1000000, 8.50, 240, 8678.23",
            "100000, 12,    12,  8884.88"
    })
    void matchesTheStandardReducingBalanceFormula(String principal, String rate, int months, String expected) {
        assertThat(calculator.emi(new BigDecimal(principal), new BigDecimal(rate), months))
                .isEqualByComparingTo(expected);
    }

    @Test
    void zeroRateSplitsPrincipalEvenly() {
        assertThat(calculator.emi(new BigDecimal("12000"), BigDecimal.ZERO, 12)).isEqualByComparingTo("1000.00");
    }

    @Test
    void scheduleRepaysExactlyThePrincipalAndClosesAtZero() {
        BigDecimal principal = new BigDecimal("250000.00");
        List<EmiCalculator.Installment> schedule = calculator.schedule(principal, new BigDecimal("9.25"), 36, null);

        assertThat(schedule).hasSize(36);
        assertThat(schedule.getLast().closingPrincipal()).isEqualByComparingTo("0");
        BigDecimal principalRepaid = schedule.stream().map(EmiCalculator.Installment::principal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(principalRepaid).isEqualByComparingTo(principal);
        // every row is internally consistent: payment = principal part + interest part
        assertThat(schedule).allSatisfy(row ->
                assertThat(row.principal().add(row.interest())).isEqualByComparingTo(row.emi()));
        // interest falls as the balance falls
        assertThat(schedule.getFirst().interest()).isGreaterThan(schedule.getLast().interest());
    }

    @Test
    void dueDatesAreCountedFromDisbursalSoMonthEndsDoNotDrift() {
        List<EmiCalculator.Installment> schedule =
                calculator.schedule(new BigDecimal("60000"), new BigDecimal("10"), 3, LocalDate.of(2026, 1, 31));
        assertThat(schedule).extracting(EmiCalculator.Installment::dueDate)
                .containsExactly(LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 31), LocalDate.of(2026, 4, 30));
    }

    @Test
    void rejectsNonsenseInput() {
        assertThatThrownBy(() -> calculator.emi(BigDecimal.ZERO, BigDecimal.TEN, 12))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> calculator.emi(BigDecimal.TEN, BigDecimal.TEN, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
