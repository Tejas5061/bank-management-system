package com.bankms.dto.loan;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoanDecisionRequest(@NotBlank @Size(max = 500) String remarks) {
}
