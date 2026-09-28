package com.bankms.dto.loan;

import com.bankms.entity.LoanStatus;
import com.bankms.entity.LoanType;

import java.math.BigDecimal;
import java.time.Instant;

public record LoanResponse(
        Long id,
        String loanNumber,
        LoanType loanType,
        BigDecimal principal,
        BigDecimal interestRate,
        int tenureMonths,
        BigDecimal emiAmount,
        BigDecimal outstandingPrincipal,
        String purpose,
        LoanStatus status,
        Long accountId,
        String accountNumber,
        Long customerId,
        String customerNumber,
        String customerName,
        Instant appliedAt,
        Instant reviewedAt,
        String reviewRemarks,
        Instant disbursedAt,
        Instant closedAt) {
}
