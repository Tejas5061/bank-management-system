package com.bankms.dto.admin;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record LoanProductRequest(
        @NotNull @DecimalMin("0.01") @DecimalMax("40.00") @Digits(integer = 2, fraction = 2) BigDecimal interestRate,
        @NotNull @DecimalMin("1000.00") @Digits(integer = 13, fraction = 2) BigDecimal minAmount,
        @NotNull @DecimalMin("1000.00") @Digits(integer = 13, fraction = 2) BigDecimal maxAmount,
        @Min(1) @Max(480) int minTenureMonths,
        @Min(1) @Max(480) int maxTenureMonths) {

    @AssertTrue(message = "minAmount must not exceed maxAmount")
    public boolean isAmountRangeValid() {
        return minAmount == null || maxAmount == null || minAmount.compareTo(maxAmount) <= 0;
    }

    @AssertTrue(message = "minTenureMonths must not exceed maxTenureMonths")
    public boolean isTenureRangeValid() {
        return minTenureMonths <= maxTenureMonths;
    }
}
