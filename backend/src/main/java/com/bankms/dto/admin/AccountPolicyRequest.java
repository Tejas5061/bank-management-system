package com.bankms.dto.admin;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record AccountPolicyRequest(
        @NotNull @DecimalMin("0.00") @DecimalMax("30.00") @Digits(integer = 2, fraction = 2) BigDecimal interestRate,
        @NotNull @DecimalMin("0.00") @Digits(integer = 13, fraction = 2) BigDecimal minimumBalance,
        @NotNull @DecimalMin("0.00") @Digits(integer = 13, fraction = 2) BigDecimal dailyTransferLimit) {
}
