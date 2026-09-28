package com.bankms.service.calc;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Savings interest by the daily-product method: interest accrues on each day's closing balance,
 * so money that sat in the account for 3 days earns 3 days of interest (not a whole month).
 * <pre>
 *   interest = (sum of daily closing balances) * rate / 100 / 365
 * </pre>
 * Closing balances come straight from the ledger's balance_after column, so no separate daily
 * balance snapshot table is needed.
 */
@Component
public class InterestCalculator {

    private static final MathContext MC = MathContext.DECIMAL128;
    private static final BigDecimal DAYS_IN_YEAR = new BigDecimal("365");
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    /**
     * @param openingBalance balance at the end of the day before the period starts
     * @param movements      successful ledger entries within the period, in posting order
     */
    public Result monthlyInterest(BigDecimal openingBalance, List<BalancePoint> movements,
                                  YearMonth period, BigDecimal annualRatePercent) {
        Map<LocalDate, BigDecimal> closingByDay = new HashMap<>();
        for (BalancePoint movement : movements) {
            closingByDay.put(movement.valueDate(), movement.balanceAfter()); // later entries win
        }

        BigDecimal running = openingBalance;
        BigDecimal dailyProduct = BigDecimal.ZERO;
        for (LocalDate day = period.atDay(1); !day.isAfter(period.atEndOfMonth()); day = day.plusDays(1)) {
            running = closingByDay.getOrDefault(day, running);
            dailyProduct = dailyProduct.add(running);
        }

        BigDecimal averageBalance = dailyProduct.divide(BigDecimal.valueOf(period.lengthOfMonth()), 2, RoundingMode.HALF_EVEN);
        BigDecimal interest = dailyProduct.multiply(annualRatePercent, MC)
                .divide(HUNDRED, MC)
                .divide(DAYS_IN_YEAR, MC)
                .setScale(2, RoundingMode.HALF_EVEN);
        return new Result(averageBalance, interest);
    }

    public record BalancePoint(LocalDate valueDate, BigDecimal balanceAfter) {
    }

    public record Result(BigDecimal averageBalance, BigDecimal interest) {
    }
}
