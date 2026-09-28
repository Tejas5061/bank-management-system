package com.bankms.service.calc;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Reducing-balance EMI:
 * <pre>
 *   EMI = P * r * (1 + r)^n / ((1 + r)^n - 1)      r = annual rate / 12 / 100, n = months
 * </pre>
 * Intermediate maths runs at 34 significant digits (DECIMAL128); only the final amounts are
 * rounded to paise. Each month's interest is charged on the outstanding principal, and the last
 * instalment absorbs the rounding residue so the loan closes at exactly zero.
 */
@Component
public class EmiCalculator {

    private static final MathContext MC = MathContext.DECIMAL128;
    private static final BigDecimal MONTHLY_DIVISOR = new BigDecimal("1200");

    public BigDecimal emi(BigDecimal principal, BigDecimal annualRatePercent, int months) {
        validate(principal, annualRatePercent, months);
        if (annualRatePercent.signum() == 0) {
            return principal.divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);
        }
        BigDecimal r = monthlyRate(annualRatePercent);
        BigDecimal growth = BigDecimal.ONE.add(r).pow(months, MC);
        return principal.multiply(r, MC)
                .multiply(growth, MC)
                .divide(growth.subtract(BigDecimal.ONE), MC)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** Due dates are counted from the disbursal date (disbursal + k months), so month ends do not drift. */
    public List<Installment> schedule(BigDecimal principal, BigDecimal annualRatePercent, int months,
                                      LocalDate disbursalDate) {
        BigDecimal emi = emi(principal, annualRatePercent, months);
        BigDecimal r = monthlyRate(annualRatePercent);
        BigDecimal outstanding = principal;
        List<Installment> installments = new ArrayList<>(months);
        for (int k = 1; k <= months; k++) {
            BigDecimal interest = outstanding.multiply(r, MC).setScale(2, RoundingMode.HALF_UP);
            boolean last = k == months;
            BigDecimal principalPart = last ? outstanding : emi.subtract(interest).min(outstanding);
            BigDecimal payment = last ? principalPart.add(interest) : emi;
            outstanding = outstanding.subtract(principalPart);
            installments.add(new Installment(
                    k,
                    disbursalDate == null ? null : disbursalDate.plusMonths(k),
                    payment,
                    principalPart,
                    interest,
                    outstanding));
        }
        return installments;
    }

    private static BigDecimal monthlyRate(BigDecimal annualRatePercent) {
        return annualRatePercent.divide(MONTHLY_DIVISOR, MC);
    }

    private static void validate(BigDecimal principal, BigDecimal annualRatePercent, int months) {
        if (principal == null || principal.signum() <= 0) {
            throw new IllegalArgumentException("principal must be positive");
        }
        if (annualRatePercent == null || annualRatePercent.signum() < 0) {
            throw new IllegalArgumentException("rate must not be negative");
        }
        if (months <= 0) {
            throw new IllegalArgumentException("tenure must be at least one month");
        }
    }

    public record Installment(int number, LocalDate dueDate, BigDecimal emi, BigDecimal principal,
                              BigDecimal interest, BigDecimal closingPrincipal) {
    }
}
