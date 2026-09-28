package com.bankms.dto.deposit;

import com.bankms.entity.FixedDepositStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FixedDepositResponse(
        Long id,
        Long accountId,
        String accountNumber,
        String payoutAccountNumber,
        BigDecimal principal,
        BigDecimal interestRate,
        int tenureMonths,
        LocalDate startDate,
        LocalDate maturityDate,
        BigDecimal maturityAmount,
        BigDecimal interestEarned,
        FixedDepositStatus status) {
}
