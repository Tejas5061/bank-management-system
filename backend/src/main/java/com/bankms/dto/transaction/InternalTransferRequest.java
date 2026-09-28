package com.bankms.dto.transaction;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Between two accounts owned by the same customer. */
public record InternalTransferRequest(
        @NotNull Long fromAccountId,
        @NotNull Long toAccountId,
        @NotNull @DecimalMin("1.00") @DecimalMax("100000000.00") @Digits(integer = 13, fraction = 2) BigDecimal amount,
        @Size(max = 140) String remarks) {
}
