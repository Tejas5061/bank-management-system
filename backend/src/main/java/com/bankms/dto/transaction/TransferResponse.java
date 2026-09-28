package com.bankms.dto.transaction;

import com.bankms.entity.TransactionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record TransferResponse(
        String referenceNumber,
        TransactionStatus status,
        BigDecimal amount,
        String fromAccountNumber,
        String toAccountNumber,
        String toName,
        String toIfsc,
        BigDecimal balanceAfter,
        LocalDate valueDate,
        Instant completedAt) {
}
