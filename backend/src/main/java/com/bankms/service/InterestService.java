package com.bankms.service;

import com.bankms.dto.admin.JobRunResponse;
import com.bankms.entity.Account;
import com.bankms.entity.AccountStatus;
import com.bankms.entity.AccountType;
import com.bankms.entity.InterestPosting;
import com.bankms.entity.NotificationType;
import com.bankms.entity.Transaction;
import com.bankms.entity.TransactionDirection;
import com.bankms.entity.TransactionStatus;
import com.bankms.entity.TransactionType;
import com.bankms.exception.BankException;
import com.bankms.repository.AccountRepository;
import com.bankms.repository.InterestPostingRepository;
import com.bankms.repository.TransactionRepository;
import com.bankms.service.calc.InterestCalculator;
import com.bankms.service.ledger.LedgerService;
import com.bankms.service.ledger.PostingDetails;
import com.bankms.service.notification.NotificationService;
import com.bankms.util.Masking;
import com.bankms.util.Money;
import com.bankms.util.ReferenceGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Monthly savings interest. Safe to run more than once for the same month: a UNIQUE
 * (account_id, period) row in interest_postings is written in the same transaction as the credit,
 * so a second run (or a second app instance) is rejected by the database rather than paying twice.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterestService {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);

    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final InterestPostingRepository postings;
    private final RateService rates;
    private final InterestCalculator calculator;
    private final LedgerService ledger;
    private final ReferenceGenerator references;
    private final NotificationService notifications;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public JobRunResponse postMonthlyInterest(YearMonth period) {
        if (!period.isBefore(YearMonth.now(clock))) {
            throw BankException.rule("Interest can only be posted for a completed month");
        }
        BigDecimal rate = rates.policy(AccountType.SAVINGS).getInterestRate();
        List<Long> ids = accounts.findOpenAccountIdsByType(AccountType.SAVINGS);
        int credited = 0;
        int skipped = 0;
        int failed = 0;
        BigDecimal total = Money.ZERO;
        for (Long id : ids) {
            try {
                BigDecimal interest = transactionTemplate.execute(status -> postForAccount(id, period, rate));
                if (interest != null && interest.signum() > 0) {
                    credited++;
                    total = total.add(interest);
                } else {
                    skipped++;
                }
            } catch (DataIntegrityViolationException alreadyPosted) {
                skipped++;
            } catch (RuntimeException e) {
                failed++;
                log.error("Interest posting failed for account {}", id, e);
            }
        }
        log.info("Interest run for {}: {} accounts, {} credited, {} skipped, {} failed, {} total",
                period, ids.size(), credited, skipped, failed, total);
        return new JobRunResponse("interest:" + period, ids.size(), credited, skipped, failed, total);
    }

    private BigDecimal postForAccount(Long accountId, YearMonth period, BigDecimal rate) {
        String key = period.toString();
        if (postings.existsByAccountIdAndPeriod(accountId, key)) {
            return null;
        }
        Account account = ledger.lock(accountId);
        if (account.getStatus() == AccountStatus.CLOSED) {
            return null;
        }
        LocalDate start = period.atDay(1);
        BigDecimal opening = transactions
                .findTopByAccountIdAndStatusAndValueDateBeforeOrderByIdDesc(accountId, TransactionStatus.SUCCESS, start)
                .map(Transaction::getBalanceAfter)
                .orElse(Money.ZERO);
        List<InterestCalculator.BalancePoint> movements = transactions
                .findByAccountIdAndStatusAndValueDateBetweenOrderByIdAsc(accountId, TransactionStatus.SUCCESS, start, period.atEndOfMonth())
                .stream()
                .map(t -> new InterestCalculator.BalancePoint(t.getValueDate(), t.getBalanceAfter()))
                .toList();
        InterestCalculator.Result result = calculator.monthlyInterest(opening, movements, period, rate);

        InterestPosting posting = new InterestPosting();
        posting.setAccount(account);
        posting.setPeriod(key);
        posting.setAverageBalance(result.averageBalance());
        posting.setInterestRate(rate);
        posting.setInterestAmount(result.interest());
        if (result.interest().signum() > 0) {
            String reference = references.next();
            // Interest is credited even to frozen accounts: a freeze stops the customer, not the bank.
            ledger.post(account, TransactionDirection.CREDIT, result.interest(), TransactionType.INTEREST_CREDIT, reference,
                    PostingDetails.system("Savings interest for %s @ %s%% p.a.".formatted(MONTH.format(period), rate)));
            posting.setTransactionRef(reference);
            notifications.notify(account.getCustomer().getUser(), NotificationType.TRANSACTION, "Interest credited",
                    "%s interest for %s was credited to A/c %s.".formatted(Money.format(result.interest()),
                            MONTH.format(period), Masking.accountNumber(account.getAccountNumber())));
        }
        postings.saveAndFlush(posting);
        return result.interest();
    }
}
