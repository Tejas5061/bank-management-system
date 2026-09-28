package com.bankms.service.statement;

import com.bankms.entity.Transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Everything a renderer needs; renderers never touch repositories. */
public record StatementData(
        String bankName,
        String holderName,
        String customerNumber,
        String address,
        String accountNumber,
        String accountType,
        String branchName,
        String ifsc,
        LocalDate from,
        LocalDate to,
        BigDecimal openingBalance,
        BigDecimal closingBalance,
        BigDecimal totalCredits,
        BigDecimal totalDebits,
        List<Transaction> rows,
        Instant generatedAt) {
}
