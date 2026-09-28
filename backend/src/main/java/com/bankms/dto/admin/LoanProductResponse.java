package com.bankms.dto.admin;

import com.bankms.entity.LoanType;

import java.math.BigDecimal;
import java.time.Instant;

public record LoanProductResponse(
        LoanType loanType,
        BigDecimal interestRate,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        int minTenureMonths,
        int maxTenureMonths,
        Instant updatedAt) {
}
