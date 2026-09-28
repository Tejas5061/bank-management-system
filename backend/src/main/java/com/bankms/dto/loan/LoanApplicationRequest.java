package com.bankms.dto.loan;

import com.bankms.entity.LoanType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Product-specific bounds (amount, tenure) are checked against the admin-managed loan product. */
public record LoanApplicationRequest(
        @NotNull LoanType loanType,
        @NotNull @DecimalMin("1000.00") @Digits(integer = 13, fraction = 2) BigDecimal amount,
        @Min(1) @Max(480) int tenureMonths,
        @NotNull Long accountId,
        @NotBlank @Size(max = 255) String purpose) {
}
