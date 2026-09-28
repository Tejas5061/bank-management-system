package com.bankms.dto.transaction;

import com.bankms.util.ValidationPatterns;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Teller cash-desk operation. Cash above 10 lakh per ticket goes through a different process. */
public record CashRequest(
        @NotBlank @Pattern(regexp = ValidationPatterns.ACCOUNT_NUMBER, message = "must be 9-18 digits") String accountNumber,
        @NotNull @DecimalMin("1.00") @DecimalMax("1000000.00") @Digits(integer = 13, fraction = 2) BigDecimal amount,
        @Size(max = 140) String remarks) {
}
