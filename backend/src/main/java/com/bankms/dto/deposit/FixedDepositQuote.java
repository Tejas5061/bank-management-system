package com.bankms.dto.deposit;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FixedDepositQuote(
        BigDecimal principal,
        BigDecimal interestRate,
        int tenureMonths,
        LocalDate maturityDate,
        BigDecimal maturityAmount,
        BigDecimal interestEarned) {
}
