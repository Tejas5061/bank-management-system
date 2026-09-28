package com.bankms.dto.transaction;

import com.bankms.entity.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;

public record CashResponse(
        String referenceNumber,
        String accountNumber,
        String holderName,
        TransactionType type,
        BigDecimal amount,
        BigDecimal balanceAfter,
        Instant completedAt) {
}
