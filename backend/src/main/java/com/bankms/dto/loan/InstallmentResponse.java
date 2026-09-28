package com.bankms.dto.loan;

import com.bankms.entity.InstallmentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record InstallmentResponse(
        int installmentNumber,
        LocalDate dueDate,
        BigDecimal emiAmount,
        BigDecimal principalComponent,
        BigDecimal interestComponent,
        BigDecimal closingPrincipal,
        InstallmentStatus status,
        Instant paidAt,
        String transactionRef,
        String lastFailureReason) {
}
