package com.bankms.dto.account;

import com.bankms.entity.AccountStatus;
import com.bankms.entity.AccountType;

import java.math.BigDecimal;
import java.time.Instant;

/** availableBalance = balance minus the minimum balance that must stay in the account. */
public record AccountResponse(
        Long id,
        String accountNumber,
        AccountType accountType,
        AccountStatus status,
        String statusReason,
        BigDecimal balance,
        BigDecimal availableBalance,
        BigDecimal minimumBalance,
        BigDecimal interestRate,
        String currency,
        String branchCode,
        String branchName,
        String ifsc,
        Instant openedAt,
        Instant closedAt) {
}
