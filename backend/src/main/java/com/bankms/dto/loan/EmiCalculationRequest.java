package com.bankms.dto.loan;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record EmiCalculationRequest(
        @NotNull @DecimalMin("1000.00") @DecimalMax("1000000000.00") @Digits(integer = 13, fraction = 2) BigDecimal principal,
        @NotNull @DecimalMin("0.01") @DecimalMax("40.00") @Digits(integer = 2, fraction = 2) BigDecimal annualRate,
        @Min(1) @Max(480) int tenureMonths) {
}
