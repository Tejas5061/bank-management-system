package com.bankms.dto.admin;

import com.bankms.entity.AccountType;

import java.math.BigDecimal;
import java.time.Instant;

public record AccountPolicyResponse(
        AccountType accountType,
        BigDecimal interestRate,
        BigDecimal minimumBalance,
        BigDecimal dailyTransferLimit,
        Instant updatedAt) {
}
