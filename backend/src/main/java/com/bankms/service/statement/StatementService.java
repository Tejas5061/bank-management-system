package com.bankms.service.statement;

import com.bankms.config.AppProperties;
import com.bankms.entity.Account;
import com.bankms.entity.Customer;
import com.bankms.entity.Transaction;
import com.bankms.entity.TransactionDirection;
import com.bankms.entity.TransactionStatus;
import com.bankms.exception.BankException;
import com.bankms.repository.AccountRepository;
import com.bankms.repository.TransactionRepository;
import com.bankms.util.Money;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Statements list successful movements only; declined attempts stay in the in-app history. */
@Service
@RequiredArgsConstructor
public class StatementService {

    private static final long MAX_DAYS = 366;

    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final PdfStatementRenderer pdf;
    private final CsvStatementRenderer csv;
    private final AppProperties properties;
    private final Clock clock;

    @Transactional(readOnly = true)
    public StatementFile generate(Long accountId, LocalDate from, LocalDate to, StatementFormat format) {
        LocalDate today = LocalDate.now(clock);
        LocalDate end = to != null ? to : today;
        LocalDate start = from != null ? from : end.minusDays(29);
        if (start.isAfter(end)) {
            throw BankException.rule("'from' must be on or before 'to'");
        }
        if (ChronoUnit.DAYS.between(start, end) >= MAX_DAYS) {
            throw BankException.rule("A statement can cover at most one year");
        }

        Account account = accounts.findWithOwnerById(accountId)
                .orElseThrow(() -> BankException.notFound("Account", accountId));
        BigDecimal opening = transactions
                .findTopByAccountIdAndStatusAndValueDateBeforeOrderByIdDesc(accountId, TransactionStatus.SUCCESS, start)
                .map(Transaction::getBalanceAfter)
                .orElse(Money.ZERO);
        List<Transaction> rows = transactions
                .findByAccountIdAndStatusAndValueDateBetweenOrderByIdAsc(accountId, TransactionStatus.SUCCESS, start, end);
        BigDecimal credits = sum(rows, TransactionDirection.CREDIT);
        BigDecimal debits = sum(rows, TransactionDirection.DEBIT);
        BigDecimal closing = rows.isEmpty() ? opening : rows.getLast().getBalanceAfter();

        Customer customer = account.getCustomer();
        StatementData data = new StatementData(
                properties.bank().name(),
                customer.getUser().getFullName(),
                customer.getCustomerNumber(),
                "%s, %s, %s %s".formatted(customer.getAddressLine(), customer.getCity(), customer.getState(), customer.getPincode()),
                account.getAccountNumber(),
                account.getAccountType().name().replace('_', ' '),
                account.getBranch().getName(),
                account.getBranch().getIfsc(),
                start, end, opening, closing, credits, debits, rows, Instant.now(clock));

        String base = "statement-%s-%s-to-%s".formatted(account.getAccountNumber(), start, end);
        return switch (format) {
            case PDF -> new StatementFile(base + ".pdf", "application/pdf", pdf.render(data, properties.bank().timezone()));
            case CSV -> new StatementFile(base + ".csv", "text/csv", csv.render(data));
        };
    }

    private static BigDecimal sum(List<Transaction> rows, TransactionDirection direction) {
        return rows.stream()
                .filter(t -> t.getDirection() == direction)
                .map(Transaction::getAmount)
                .reduce(Money.ZERO, BigDecimal::add);
    }
}
