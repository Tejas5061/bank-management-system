package com.bankms.dto.transaction;

import com.bankms.entity.TransactionChannel;
import com.bankms.entity.TransactionDirection;
import com.bankms.entity.TransactionStatus;
import com.bankms.entity.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record TransactionResponse(
        Long id,
        String referenceNumber,
        Long accountId,
        String accountNumber,
        TransactionType type,
        TransactionDirection direction,
        BigDecimal amount,
        BigDecimal balanceAfter,
        TransactionStatus status,
        TransactionChannel channel,
        String description,
        String counterpartyAccount,
        String counterpartyName,
        String counterpartyIfsc,
        String failureReason,
        LocalDate valueDate,
        Instant createdAt) {
}
