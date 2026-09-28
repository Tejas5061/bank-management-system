package com.bankms.service.ledger;

import com.bankms.entity.Account;
import com.bankms.entity.AccountPolicy;
import com.bankms.entity.AccountStatus;
import com.bankms.entity.Transaction;
import com.bankms.entity.TransactionDirection;
import com.bankms.entity.TransactionStatus;
import com.bankms.entity.TransactionType;
import com.bankms.exception.BankException;
import com.bankms.exception.ErrorCode;
import com.bankms.exception.TransactionDeclinedException;
import com.bankms.repository.TransactionRepository;
import com.bankms.service.RateService;
import com.bankms.util.Masking;
import com.bankms.util.Money;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Low-level double-entry primitives. Every method requires a surrounding transaction
 * (Propagation.MANDATORY): locks are only meaningful inside one, and a caller that forgets
 * {@code @Transactional} fails loudly instead of moving money without isolation.
 */
@Service
@RequiredArgsConstructor
public class LedgerService {

    private final EntityManager entityManager;
    private final TransactionRepository transactions;
    private final RateService rates;
    private final Clock clock;

    /**
     * SELECT ... FOR UPDATE that always returns the row's current state.
     * <p>
     * A locking JPQL query is not enough: if this account was already read into the persistence
     * context earlier in the transaction (say, while loading a loan and its account), Hibernate
     * locks the row but hands back the cached, possibly stale, instance. refresh() with a
     * pessimistic lock re-reads the row under the lock, whatever was cached before.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Account lock(Long accountId) {
        Account account = entityManager.getReference(Account.class, accountId);
        try {
            entityManager.refresh(account, LockModeType.PESSIMISTIC_WRITE);
        } catch (EntityNotFoundException e) {
            throw BankException.notFound("Account", accountId);
        }
        return account;
    }

    /**
     * Locks accounts in ascending id order, whatever order the caller names them in.
     * <p>
     * If A→B locked A then B while B→A locked B then A, each would hold one lock and wait forever
     * for the other (InnoDB would kill one as a deadlock victim). With a global order both
     * transfers try A first, so one simply waits for the other.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Map<Long, Account> lockInOrder(Long... accountIds) {
        Map<Long, Account> locked = new LinkedHashMap<>();
        Arrays.stream(accountIds)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .forEach(id -> locked.put(id, lock(id)));
        return locked;
    }

    /** KYC and status gate for the account initiating a movement. Declines are recorded as FAILED rows. */
    public void requireCanTransact(Account account, TransactionType type, BigDecimal amount) {
        if (!account.getCustomer().isKycVerified()) {
            throw declined(ErrorCode.KYC_NOT_VERIFIED,
                    "KYC is not verified for account " + Masking.accountNumber(account.getAccountNumber()),
                    account, type, amount);
        }
        switch (account.getStatus()) {
            case FROZEN -> throw declined(ErrorCode.ACCOUNT_FROZEN,
                    "Account " + Masking.accountNumber(account.getAccountNumber()) + " is frozen", account, type, amount);
            case CLOSED -> throw declined(ErrorCode.ACCOUNT_CLOSED,
                    "Account " + Masking.accountNumber(account.getAccountNumber()) + " is closed", account, type, amount);
            case ACTIVE -> {
                // ok
            }
        }
    }

    /** Same gate for the receiving side; failures there are the payee's problem, so nothing is recorded. */
    public void requireCanReceive(Account account) {
        if (!account.getCustomer().isKycVerified()) {
            throw new BankException(ErrorCode.KYC_NOT_VERIFIED, "The destination account cannot receive funds until its KYC is verified");
        }
        if (account.getStatus() != AccountStatus.ACTIVE) {
            ErrorCode code = account.getStatus() == AccountStatus.FROZEN
                    ? ErrorCode.ACCOUNT_FROZEN : ErrorCode.ACCOUNT_CLOSED;
            throw new BankException(code, "The destination account is " + account.getStatus().name().toLowerCase());
        }
    }

    /**
     * @param enforceMinimumBalance false for bank-initiated debits such as EMIs, which may take the
     *                              balance below the minimum (a real bank would levy a charge)
     */
    public void requireSufficientFunds(Account account, BigDecimal amount, TransactionType type,
                                       boolean enforceMinimumBalance) {
        BigDecimal minimum = enforceMinimumBalance ? rates.policy(account.getAccountType()).getMinimumBalance() : Money.ZERO;
        BigDecimal available = account.getBalance().subtract(minimum);
        if (available.compareTo(amount) < 0) {
            String message = minimum.signum() > 0
                    ? "Insufficient funds: you can move up to %s (a minimum balance of %s must be maintained)"
                    .formatted(Money.format(available.max(Money.ZERO)), Money.format(minimum))
                    : "Insufficient funds: available balance is " + Money.format(account.getBalance());
            throw declined(ErrorCode.INSUFFICIENT_FUNDS, message, account, type, amount);
        }
    }

    /** Must run with the account row locked, otherwise two parallel transfers could both fit the limit. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void requireWithinDailyLimit(Account account, BigDecimal amount) {
        AccountPolicy policy = rates.policy(account.getAccountType());
        BigDecimal usedToday = transactions.sumSuccessfulByTypeOnDate(account.getId(), TransactionType.TRANSFER_OUT, today());
        if (usedToday.add(amount).compareTo(policy.getDailyTransferLimit()) > 0) {
            BigDecimal remaining = policy.getDailyTransferLimit().subtract(usedToday).max(Money.ZERO);
            throw declined(ErrorCode.DAILY_LIMIT_EXCEEDED,
                    "Daily transfer limit of %s exceeded; %s remaining today"
                            .formatted(Money.format(policy.getDailyTransferLimit()), Money.format(remaining)),
                    account, TransactionType.TRANSFER_OUT, amount);
        }
    }

    /** Applies the movement to the (locked) account and appends the ledger row with the new balance. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Transaction post(Account account, TransactionDirection direction, BigDecimal amount,
                           TransactionType type, String reference, PostingDetails details) {
        if (direction == TransactionDirection.DEBIT) {
            account.debit(amount);
        } else {
            account.credit(amount);
        }
        return transactions.save(Transaction.builder()
                .referenceNumber(reference)
                .account(account)
                .type(type)
                .direction(direction)
                .amount(amount)
                .balanceAfter(account.getBalance())
                .status(TransactionStatus.SUCCESS)
                .channel(details.channel())
                .description(details.description())
                .counterpartyAccount(details.counterpartyAccount())
                .counterpartyName(details.counterpartyName())
                .counterpartyIfsc(details.counterpartyIfsc())
                .initiatedBy(details.initiatedBy())
                .valueDate(today())
                .build());
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    private static TransactionDeclinedException declined(ErrorCode code, String message, Account account,
                                                         TransactionType type, BigDecimal amount) {
        return new TransactionDeclinedException(code, message, account.getId(), type, amount, account.getBalance());
    }
}
