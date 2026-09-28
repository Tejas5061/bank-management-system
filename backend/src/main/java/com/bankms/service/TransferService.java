package com.bankms.service;

import com.bankms.config.AppProperties;
import com.bankms.dto.transaction.BeneficiaryTransferRequest;
import com.bankms.dto.transaction.CashRequest;
import com.bankms.dto.transaction.CashResponse;
import com.bankms.dto.transaction.InternalTransferRequest;
import com.bankms.dto.transaction.TransferResponse;
import com.bankms.entity.Account;
import com.bankms.entity.Beneficiary;
import com.bankms.entity.IdempotencyRecord;
import com.bankms.entity.NotificationType;
import com.bankms.entity.Transaction;
import com.bankms.entity.TransactionDirection;
import com.bankms.entity.TransactionType;
import com.bankms.exception.BankException;
import com.bankms.exception.ErrorCode;
import com.bankms.repository.AccountRepository;
import com.bankms.repository.BeneficiaryRepository;
import com.bankms.service.idempotency.IdempotencyContext;
import com.bankms.service.idempotency.IdempotencyService;
import com.bankms.service.ledger.LedgerService;
import com.bankms.service.ledger.PostingDetails;
import com.bankms.service.notification.NotificationService;
import com.bankms.util.Masking;
import com.bankms.util.Money;
import com.bankms.util.ReferenceGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

/**
 * The money-moving transactions. Each method is one database transaction that:
 * <ol>
 *   <li>reserves the Idempotency-Key (first write, see IdempotencyService),</li>
 *   <li>locks every account it touches, in ascending id order (no deadlocks),</li>
 *   <li>re-validates everything against the locked rows (KYC, status, funds, daily limit),</li>
 *   <li>moves the money and writes both ledger legs with the same reference number,</li>
 *   <li>stores the response under the key, then commits.</li>
 * </ol>
 * READ_COMMITTED is deliberate. Under MySQL's default REPEATABLE READ the read snapshot is fixed
 * by the first plain SELECT, which happens BEFORE we obtain the row locks; the daily-limit SUM
 * would then miss a transfer that committed while we waited for the lock. With READ_COMMITTED
 * every read after the lock sees the latest committed ledger.
 */
@Service
@RequiredArgsConstructor
public class TransferService {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.ENGLISH);

    private final AccountRepository accounts;
    private final BeneficiaryRepository beneficiaries;
    private final LedgerService ledger;
    private final IdempotencyService idempotency;
    private final ReferenceGenerator references;
    private final NotificationService notifications;
    private final AppProperties properties;
    private final Clock clock;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TransferResponse internalTransfer(IdempotencyContext key, Long userId, InternalTransferRequest request) {
        IdempotencyRecord record = idempotency.reserve(key);
        if (request.fromAccountId().equals(request.toAccountId())) {
            throw BankException.rule("Choose two different accounts");
        }
        requireOwner(request.fromAccountId(), userId);
        requireOwner(request.toAccountId(), userId);
        BigDecimal amount = Money.normalize(request.amount());

        Map<Long, Account> locked = ledger.lockInOrder(request.fromAccountId(), request.toAccountId());
        Account from = locked.get(request.fromAccountId());
        Account to = locked.get(request.toAccountId());
        requireOperative(from);
        requireOperative(to);
        ledger.requireCanTransact(from, TransactionType.TRANSFER_OUT, amount);
        ledger.requireCanReceive(to);
        ledger.requireSufficientFunds(from, amount, TransactionType.TRANSFER_OUT, true);
        ledger.requireWithinDailyLimit(from, amount);

        String reference = references.next();
        String remarks = remarksOr(request.remarks(), "Own account transfer");
        String holder = from.getCustomer().getUser().getFullName();
        Transaction debit = ledger.post(from, TransactionDirection.DEBIT, amount, TransactionType.TRANSFER_OUT, reference,
                PostingDetails.online(remarks, userId, to.getAccountNumber(), holder, to.getBranch().getIfsc()));
        ledger.post(to, TransactionDirection.CREDIT, amount, TransactionType.TRANSFER_IN, reference,
                PostingDetails.online(remarks, userId, from.getAccountNumber(), holder, from.getBranch().getIfsc()));

        notifications.notify(from.getCustomer().getUser(), NotificationType.TRANSACTION, "Transfer successful",
                "%s moved from A/c %s to A/c %s. Ref %s".formatted(Money.format(amount),
                        Masking.accountNumber(from.getAccountNumber()), Masking.accountNumber(to.getAccountNumber()), reference));

        TransferResponse response = new TransferResponse(reference, debit.getStatus(), amount, from.getAccountNumber(),
                to.getAccountNumber(), holder, to.getBranch().getIfsc(), from.getBalance(), debit.getValueDate(), Instant.now(clock));
        idempotency.complete(record, HttpStatus.CREATED.value(), response);
        return response;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TransferResponse beneficiaryTransfer(IdempotencyContext key, Long userId, Long customerId,
                                                BeneficiaryTransferRequest request) {
        IdempotencyRecord record = idempotency.reserve(key);
        requireOwner(request.fromAccountId(), userId);
        Beneficiary beneficiary = beneficiaries.findByIdAndCustomerId(request.beneficiaryId(), customerId)
                .orElseThrow(() -> BankException.notFound("Beneficiary", request.beneficiaryId()));
        Instant now = Instant.now(clock);
        if (!beneficiary.isActive(now)) {
            throw new BankException(ErrorCode.BENEFICIARY_COOLING_PERIOD,
                    "For your security, transfers to a new beneficiary are allowed from " + TIME.format(beneficiary.getActivatedAt().atZone(zone())));
        }
        BigDecimal amount = Money.normalize(request.amount());

        Account target = null;
        if (beneficiary.isInternal()) {
            target = accounts.findByAccountNumber(beneficiary.getAccountNumber())
                    .orElseThrow(() -> BankException.rule("The beneficiary's account no longer exists"));
            if (target.getId().equals(request.fromAccountId())) {
                throw BankException.rule("Source and beneficiary accounts are the same");
            }
        }
        Map<Long, Account> locked = ledger.lockInOrder(request.fromAccountId(), target == null ? null : target.getId());
        Account from = locked.get(request.fromAccountId());
        requireOperative(from);
        ledger.requireCanTransact(from, TransactionType.TRANSFER_OUT, amount);
        if (target != null) {
            target = locked.get(target.getId());
            requireOperative(target);
            ledger.requireCanReceive(target);
        }
        ledger.requireSufficientFunds(from, amount, TransactionType.TRANSFER_OUT, true);
        ledger.requireWithinDailyLimit(from, amount);

        String reference = references.next();
        String remarks = remarksOr(request.remarks(),
                (beneficiary.isInternal() ? "Transfer to " : "NEFT to ") + beneficiary.getName());
        String sender = from.getCustomer().getUser().getFullName();
        Transaction debit = ledger.post(from, TransactionDirection.DEBIT, amount, TransactionType.TRANSFER_OUT, reference,
                PostingDetails.online(remarks, userId, beneficiary.getAccountNumber(), beneficiary.getName(), beneficiary.getIfsc()));
        if (target != null) {
            ledger.post(target, TransactionDirection.CREDIT, amount, TransactionType.TRANSFER_IN, reference,
                    PostingDetails.online("Transfer from " + sender, userId, from.getAccountNumber(), sender, from.getBranch().getIfsc()));
            notifications.notifyAndEmail(target.getCustomer().getUser(), NotificationType.TRANSACTION, "Money received",
                    "%s credited to A/c %s from %s. Ref %s".formatted(Money.format(amount),
                            Masking.accountNumber(target.getAccountNumber()), sender, reference));
        }
        notifications.notifyAndEmail(from.getCustomer().getUser(), NotificationType.TRANSACTION, "Debit alert",
                "%s debited from A/c %s to %s (%s). Available balance: %s. Ref %s".formatted(Money.format(amount),
                        Masking.accountNumber(from.getAccountNumber()), beneficiary.getName(),
                        Masking.accountNumber(beneficiary.getAccountNumber()), Money.format(from.getBalance()), reference));

        TransferResponse response = new TransferResponse(reference, debit.getStatus(), amount, from.getAccountNumber(),
                beneficiary.getAccountNumber(), beneficiary.getName(), beneficiary.getIfsc(), from.getBalance(),
                debit.getValueDate(), now);
        idempotency.complete(record, HttpStatus.CREATED.value(), response);
        return response;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CashResponse cashDeposit(IdempotencyContext key, Long tellerUserId, CashRequest request) {
        IdempotencyRecord record = idempotency.reserve(key);
        Account account = ledger.lock(accountIdFor(request.accountNumber()));
        BigDecimal amount = Money.normalize(request.amount());
        requireOperative(account);
        ledger.requireCanTransact(account, TransactionType.DEPOSIT, amount);

        String reference = references.next();
        ledger.post(account, TransactionDirection.CREDIT, amount, TransactionType.DEPOSIT, reference,
                PostingDetails.branch(remarksOr(request.remarks(), "Cash deposit"), tellerUserId));
        notifications.notifyAndEmail(account.getCustomer().getUser(), NotificationType.TRANSACTION, "Cash deposited",
                "%s cash deposited to A/c %s at the branch. Balance: %s. Ref %s".formatted(Money.format(amount),
                        Masking.accountNumber(account.getAccountNumber()), Money.format(account.getBalance()), reference));
        return complete(record, account, TransactionType.DEPOSIT, amount, reference);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CashResponse cashWithdrawal(IdempotencyContext key, Long tellerUserId, CashRequest request) {
        IdempotencyRecord record = idempotency.reserve(key);
        Account account = ledger.lock(accountIdFor(request.accountNumber()));
        BigDecimal amount = Money.normalize(request.amount());
        requireOperative(account);
        ledger.requireCanTransact(account, TransactionType.WITHDRAWAL, amount);
        ledger.requireSufficientFunds(account, amount, TransactionType.WITHDRAWAL, true);

        String reference = references.next();
        ledger.post(account, TransactionDirection.DEBIT, amount, TransactionType.WITHDRAWAL, reference,
                PostingDetails.branch(remarksOr(request.remarks(), "Cash withdrawal"), tellerUserId));
        notifications.notifyAndEmail(account.getCustomer().getUser(), NotificationType.TRANSACTION, "Cash withdrawn",
                "%s cash withdrawn from A/c %s at the branch. Balance: %s. Ref %s".formatted(Money.format(amount),
                        Masking.accountNumber(account.getAccountNumber()), Money.format(account.getBalance()), reference));
        return complete(record, account, TransactionType.WITHDRAWAL, amount, reference);
    }

    private CashResponse complete(IdempotencyRecord record, Account account, TransactionType type, BigDecimal amount,
                                  String reference) {
        CashResponse response = new CashResponse(reference, account.getAccountNumber(),
                account.getCustomer().getUser().getFullName(), type, amount, account.getBalance(), Instant.now(clock));
        idempotency.complete(record, HttpStatus.CREATED.value(), response);
        return response;
    }

    /** Ownership is checked before locking, so probing someone else's account id locks nothing. */
    private void requireOwner(Long accountId, Long userId) {
        if (!accounts.isOwnedByUser(accountId, userId)) {
            throw BankException.notFound("Account", accountId);
        }
    }

    private Long accountIdFor(String accountNumber) {
        return accounts.findByAccountNumber(accountNumber).map(Account::getId)
                .orElseThrow(() -> BankException.notFound("Account", accountNumber));
    }

    private static void requireOperative(Account account) {
        if (account.isFixedDeposit()) {
            throw BankException.rule("Fixed deposit accounts cannot be used for transfers or cash; they pay out on maturity");
        }
    }

    private static String remarksOr(String remarks, String fallback) {
        return StringUtils.hasText(remarks) ? remarks.trim() : fallback;
    }

    private ZoneId zone() {
        return properties.bank().timezone();
    }
}
