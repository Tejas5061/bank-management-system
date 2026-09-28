package com.bankms.service;

import com.bankms.dto.admin.JobRunResponse;
import com.bankms.dto.common.PageResponse;
import com.bankms.dto.deposit.FixedDepositQuote;
import com.bankms.dto.deposit.FixedDepositRequest;
import com.bankms.dto.deposit.FixedDepositResponse;
import com.bankms.entity.Account;
import com.bankms.entity.AccountPolicy;
import com.bankms.entity.AccountStatus;
import com.bankms.entity.AccountType;
import com.bankms.entity.FixedDeposit;
import com.bankms.entity.FixedDepositStatus;
import com.bankms.entity.IdempotencyRecord;
import com.bankms.entity.NotificationType;
import com.bankms.entity.TransactionDirection;
import com.bankms.entity.TransactionType;
import com.bankms.exception.BankException;
import com.bankms.mapper.FixedDepositMapper;
import com.bankms.repository.AccountRepository;
import com.bankms.repository.FixedDepositRepository;
import com.bankms.service.calc.FixedDepositCalculator;
import com.bankms.service.idempotency.IdempotencyContext;
import com.bankms.service.idempotency.IdempotencyService;
import com.bankms.service.ledger.LedgerService;
import com.bankms.service.ledger.PostingDetails;
import com.bankms.service.notification.NotificationService;
import com.bankms.util.Masking;
import com.bankms.util.Money;
import com.bankms.util.ReferenceGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FixedDepositService {

    private final FixedDepositRepository fixedDeposits;
    private final AccountRepository accounts;
    private final AccountService accountService;
    private final LedgerService ledger;
    private final RateService rates;
    private final FixedDepositCalculator calculator;
    private final IdempotencyService idempotency;
    private final ReferenceGenerator references;
    private final NotificationService notifications;
    private final FixedDepositMapper mapper;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<FixedDepositResponse> listForCustomer(Long customerId, Pageable pageable) {
        return PageResponse.of(fixedDeposits.findByCustomerId(customerId, pageable), mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public FixedDepositQuote quote(BigDecimal principal, int tenureMonths) {
        AccountPolicy policy = rates.policy(AccountType.FIXED_DEPOSIT);
        BigDecimal amount = Money.normalize(principal);
        BigDecimal maturity = calculator.maturityAmount(amount, policy.getInterestRate(), tenureMonths);
        return new FixedDepositQuote(amount, policy.getInterestRate(), tenureMonths,
                LocalDate.now(clock).plusMonths(tenureMonths), maturity, maturity.subtract(amount));
    }

    /** Books an FD funded from the customer's own account. Called through TransactionService. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public FixedDepositResponse open(IdempotencyContext key, Long userId, FixedDepositRequest request) {
        IdempotencyRecord record = idempotency.reserve(key);
        if (!accounts.isOwnedByUser(request.sourceAccountId(), userId)) {
            throw BankException.notFound("Account", request.sourceAccountId());
        }
        AccountPolicy policy = rates.policy(AccountType.FIXED_DEPOSIT);
        BigDecimal principal = Money.normalize(request.principal());
        if (principal.compareTo(policy.getMinimumBalance()) < 0) {
            throw BankException.rule("The minimum fixed deposit amount is " + Money.format(policy.getMinimumBalance()));
        }

        Account source = ledger.lock(request.sourceAccountId());
        if (source.isFixedDeposit()) {
            throw BankException.rule("Fund the deposit from a savings or current account");
        }
        ledger.requireCanTransact(source, TransactionType.FD_BOOKING, principal);
        ledger.requireSufficientFunds(source, principal, TransactionType.FD_BOOKING, true);

        Account fdAccount = accountService.createAccount(source.getCustomer(), AccountType.FIXED_DEPOSIT, source.getBranch());
        LocalDate start = LocalDate.now(clock);
        FixedDeposit fd = new FixedDeposit();
        fd.setAccount(fdAccount);
        fd.setPayoutAccount(source);
        fd.setPrincipal(principal);
        fd.setInterestRate(policy.getInterestRate());
        fd.setTenureMonths(request.tenureMonths());
        fd.setStartDate(start);
        fd.setMaturityDate(start.plusMonths(request.tenureMonths()));
        fd.setMaturityAmount(calculator.maturityAmount(principal, policy.getInterestRate(), request.tenureMonths()));
        fixedDeposits.save(fd);

        String reference = references.next();
        String holder = source.getCustomer().getUser().getFullName();
        String ifsc = source.getBranch().getIfsc();
        ledger.post(source, TransactionDirection.DEBIT, principal, TransactionType.FD_BOOKING, reference,
                PostingDetails.online("Fixed deposit booked", userId, fdAccount.getAccountNumber(), holder, ifsc));
        ledger.post(fdAccount, TransactionDirection.CREDIT, principal, TransactionType.FD_BOOKING, reference,
                PostingDetails.online("Fixed deposit opened", userId, source.getAccountNumber(), holder, ifsc));

        notifications.notifyAndEmail(source.getCustomer().getUser(), NotificationType.ACCOUNT, "Fixed deposit booked",
                "Your FD %s of %s at %s%% p.a. matures on %s for %s. Proceeds will be credited to A/c %s."
                        .formatted(fdAccount.getAccountNumber(), Money.format(principal), fd.getInterestRate(),
                                fd.getMaturityDate(), Money.format(fd.getMaturityAmount()),
                                Masking.accountNumber(source.getAccountNumber())));

        FixedDepositResponse response = mapper.toResponse(fd);
        idempotency.complete(record, HttpStatus.CREATED.value(), response);
        return response;
    }

    /**
     * Pays out every FD due today or earlier. Each FD is its own transaction, so one problem
     * (say a closed payout account) does not stop the rest of the batch.
     */
    public JobRunResponse processMaturities() {
        List<Long> due = fixedDeposits.findMaturedIds(LocalDate.now(clock));
        int paid = 0;
        int skipped = 0;
        int failed = 0;
        BigDecimal total = Money.ZERO;
        for (Long id : due) {
            try {
                BigDecimal amount = transactionTemplate.execute(status -> matureOne(id));
                if (amount != null) {
                    paid++;
                    total = total.add(amount);
                } else {
                    skipped++;
                }
            } catch (RuntimeException e) {
                failed++;
                log.error("FD maturity failed for fixed deposit {}", id, e);
            }
        }
        log.info("FD maturity run: {} due, {} paid, {} skipped, {} failed, {} total", due.size(), paid, skipped, failed, total);
        return new JobRunResponse("fd-maturity", due.size(), paid, skipped, failed, total);
    }

    private BigDecimal matureOne(Long fixedDepositId) {
        // Lock the FD row first: a concurrent run then waits here and sees MATURED when it gets in.
        FixedDeposit fd = fixedDeposits.findByIdForUpdate(fixedDepositId).orElse(null);
        if (fd == null || fd.getStatus() != FixedDepositStatus.ACTIVE) {
            return null;
        }
        Map<Long, Account> locked = ledger.lockInOrder(fd.getAccount().getId(), fd.getPayoutAccount().getId());
        Account fdAccount = locked.get(fd.getAccount().getId());
        Account payout = locked.get(fd.getPayoutAccount().getId());
        if (payout.getStatus() == AccountStatus.CLOSED) {
            log.warn("Payout account for FD {} is closed; leaving it for manual settlement", fdAccount.getAccountNumber());
            return null;
        }

        Instant now = Instant.now(clock);
        String reference = references.next();
        BigDecimal interest = fd.getMaturityAmount().subtract(fd.getPrincipal());
        ledger.post(fdAccount, TransactionDirection.DEBIT, fdAccount.getBalance(), TransactionType.FD_MATURITY, reference,
                PostingDetails.system("FD matured, proceeds to A/c " + Masking.accountNumber(payout.getAccountNumber())));
        ledger.post(payout, TransactionDirection.CREDIT, fd.getMaturityAmount(), TransactionType.FD_MATURITY, reference,
                PostingDetails.system("FD %s matured: principal %s + interest %s".formatted(
                        fdAccount.getAccountNumber(), Money.format(fd.getPrincipal()), Money.format(interest))));
        fdAccount.changeStatus(AccountStatus.CLOSED, "Matured", now);
        fd.setStatus(FixedDepositStatus.MATURED);
        fd.setMaturedAt(now);

        notifications.notifyAndEmail(payout.getCustomer().getUser(), NotificationType.ACCOUNT, "Fixed deposit matured",
                "Your FD %s has matured. %s (including %s interest) has been credited to A/c %s."
                        .formatted(fdAccount.getAccountNumber(), Money.format(fd.getMaturityAmount()),
                                Money.format(interest), Masking.accountNumber(payout.getAccountNumber())));
        return fd.getMaturityAmount();
    }
}
