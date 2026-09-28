package com.bankms.service.calc;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * FD maturity with quarterly compounding, the usual convention for Indian term deposits:
 * <pre>
 *   A = P * (1 + R/400)^q        q = whole quarters in the tenure
 * </pre>
 * Months left over after the last full quarter earn simple interest on the compounded amount.
 */
@Component
public class FixedDepositCalculator {

    private static final MathContext MC = MathContext.DECIMAL128;
    private static final BigDecimal QUARTERLY_DIVISOR = new BigDecimal("400");
    private static final BigDecimal MONTHLY_DIVISOR = new BigDecimal("1200");

    public BigDecimal maturityAmount(BigDecimal principal, BigDecimal annualRatePercent, int tenureMonths) {
        if (principal == null || principal.signum() <= 0 || tenureMonths <= 0) {
            throw new IllegalArgumentException("principal and tenure must be positive");
        }
        int quarters = tenureMonths / 3;
        int brokenMonths = tenureMonths % 3;

        BigDecimal amount = principal.multiply(
                BigDecimal.ONE.add(annualRatePercent.divide(QUARTERLY_DIVISOR, MC)).pow(quarters, MC), MC);
        if (brokenMonths > 0) {
            BigDecimal simple = amount.multiply(annualRatePercent, MC)
                    .multiply(BigDecimal.valueOf(brokenMonths), MC)
                    .divide(MONTHLY_DIVISOR, MC);
            amount = amount.add(simple, MC);
        }
        return amount.setScale(2, RoundingMode.HALF_EVEN);
    }
}
