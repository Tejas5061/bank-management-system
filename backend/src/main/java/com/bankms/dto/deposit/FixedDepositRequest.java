package com.bankms.dto.deposit;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** Funded from sourceAccountId, which also receives principal plus interest on maturity. */
public record FixedDepositRequest(
        @NotNull Long sourceAccountId,
        @NotNull @DecimalMin("1000.00") @DecimalMax("100000000.00") @Digits(integer = 13, fraction = 2) BigDecimal principal,
        @Min(3) @Max(120) int tenureMonths) {
}
