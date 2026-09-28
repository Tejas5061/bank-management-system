package com.bankms.service.ledger;

import com.bankms.entity.Transaction;
import com.bankms.entity.TransactionChannel;
import com.bankms.entity.TransactionDirection;
import com.bankms.entity.TransactionStatus;
import com.bankms.entity.TransactionType;
import com.bankms.exception.TransactionDeclinedException;
import com.bankms.repository.AccountRepository;
import com.bankms.repository.TransactionRepository;
import com.bankms.util.ReferenceGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Leaves a FAILED row in the customer's history when a movement is declined.
 * <p>
 * This cannot happen inside the declining transaction, because throwing the exception rolls that
 * transaction back (taking any row we wrote with it). It is called afterwards, from the
 * non-transactional facade, once the original transaction has rolled back and released its locks,
 * so the insert here cannot wait on a lock its own caller holds.
 */
@Service
@RequiredArgsConstructor
public class DeclinedTransactionRecorder {

    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final ReferenceGenerator references;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(TransactionDeclinedException declined, TransactionChannel channel, String description,
                       Long initiatedBy) {
        TransactionType type = declined.getAttemptedType();
        transactions.save(Transaction.builder()
                .referenceNumber(references.next())
                .account(accounts.getReferenceById(declined.getAccountId()))
                .type(type)
                .direction(type == TransactionType.DEPOSIT ? TransactionDirection.CREDIT : TransactionDirection.DEBIT)
                .amount(declined.getAmount())
                .balanceAfter(declined.getBalanceAtDecline())
                .status(TransactionStatus.FAILED)
                .channel(channel)
                .description(description)
                .failureReason(declined.getMessage())
                .initiatedBy(initiatedBy)
                .valueDate(LocalDate.now(clock))
                .build());
    }
}
