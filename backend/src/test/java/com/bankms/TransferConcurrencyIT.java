package com.bankms;

import com.bankms.dto.transaction.InternalTransferRequest;
import com.bankms.dto.transaction.TransferResponse;
import com.bankms.entity.Account;
import com.bankms.entity.KycStatus;
import com.bankms.entity.Transaction;
import com.bankms.entity.TransactionDirection;
import com.bankms.entity.TransactionStatus;
import com.bankms.exception.BankException;
import com.bankms.exception.ErrorCode;
import com.bankms.repository.AccountRepository;
import com.bankms.repository.TransactionRepository;
import com.bankms.service.TransactionService;
import com.bankms.service.idempotency.Idempotent;
import com.bankms.support.AbstractIntegrationTest;
import com.bankms.support.TestDataFactory;
import com.bankms.support.TestDataFactory.TestCustomer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hammers the transfer path from many threads at once against real MySQL row locks. These are the
 * scenarios that pass with a naive implementation in a single-threaded test and then lose money
 * in production.
 */
class TransferConcurrencyIT extends AbstractIntegrationTest {

    @Autowired
    private TransactionService transactionService;
    @Autowired
    private AccountRepository accounts;
    @Autowired
    private TransactionRepository transactions;

    @Test
    void opposingTransfersNeitherDeadlockNorLoseMoney() throws Exception {
        TestCustomer owner = data.customer(KycStatus.VERIFIED, "100000.00", "100000.00");
        Long a = owner.account(0);
        Long b = owner.account(1);

        List<Callable<Outcome>> tasks = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            tasks.add(() -> transfer(owner, a, b, "100.00", TestDataFactory.key()));
            tasks.add(() -> transfer(owner, b, a, "100.00", TestDataFactory.key()));
        }
        List<Outcome> outcomes = runConcurrently(tasks);

        // A->B locks A then B; B->A ALSO locks A then B (ascending id), so nobody deadlocks.
        assertThat(outcomes).allSatisfy(o -> assertThat(o.error()).isNull());
        assertThat(balance(a)).isEqualByComparingTo("100000.00");
        assertThat(balance(b)).isEqualByComparingTo("100000.00");
        assertLedgerMatchesBalance(a);
        assertLedgerMatchesBalance(b);
    }

    @Test
    void parallelWithdrawalsCannotOverdrawOrBreachTheMinimumBalance() throws Exception {
        // 1,500 with a 1,000 minimum balance: exactly 500 may leave, i.e. five transfers of 100.
        TestCustomer owner = data.customer(KycStatus.VERIFIED, "1500.00", "0");
        List<Callable<Outcome>> tasks = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            tasks.add(() -> transfer(owner, owner.account(0), owner.account(1), "100.00", TestDataFactory.key()));
        }
        List<Outcome> outcomes = runConcurrently(tasks);

        assertThat(outcomes.stream().filter(Outcome::succeeded)).hasSize(5);
        assertThat(outcomes.stream().filter(o -> o.error() == ErrorCode.INSUFFICIENT_FUNDS)).hasSize(7);
        assertThat(balance(owner.account(0))).isEqualByComparingTo("1000.00");
        assertThat(balance(owner.account(1))).isEqualByComparingTo("500.00");
        assertLedgerMatchesBalance(owner.account(0));
        // every decline left a FAILED row behind for the customer to see
        assertThat(failedRows(owner.account(0))).isEqualTo(7);
    }

    @Test
    void dailyLimitHoldsUnderConcurrency() throws Exception {
        // Limit is 2,00,000/day. Five parallel 60,000 transfers: only three fit.
        // This is the test that fails under REPEATABLE READ (see TransferService javadoc).
        TestCustomer owner = data.customer(KycStatus.VERIFIED, "500000.00", "0");
        List<Callable<Outcome>> tasks = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            tasks.add(() -> transfer(owner, owner.account(0), owner.account(1), "60000.00", TestDataFactory.key()));
        }
        List<Outcome> outcomes = runConcurrently(tasks);

        assertThat(outcomes.stream().filter(Outcome::succeeded)).hasSize(3);
        assertThat(outcomes.stream().filter(o -> o.error() == ErrorCode.DAILY_LIMIT_EXCEEDED)).hasSize(2);
        assertThat(balance(owner.account(1))).isEqualByComparingTo("180000.00");
    }

    @Test
    void duplicateRequestsWithOneIdempotencyKeyMoveMoneyOnce() throws Exception {
        TestCustomer owner = data.customer(KycStatus.VERIFIED, "10000.00", "0");
        String key = TestDataFactory.key();
        List<Callable<Outcome>> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tasks.add(() -> transfer(owner, owner.account(0), owner.account(1), "250.00", key));
        }
        List<Outcome> outcomes = runConcurrently(tasks);

        assertThat(outcomes).allSatisfy(o -> assertThat(o.succeeded()).isTrue());
        assertThat(outcomes.stream().map(Outcome::reference).distinct()).hasSize(1);
        assertThat(outcomes.stream().filter(o -> !o.replayed())).hasSize(1);
        assertThat(balance(owner.account(0))).isEqualByComparingTo("9750.00");
        assertThat(balance(owner.account(1))).isEqualByComparingTo("250.00");
    }

    // ---------------------------------------------------------------- helpers

    private Outcome transfer(TestCustomer owner, Long from, Long to, String amount, String key) {
        try {
            Idempotent<TransferResponse> result = transactionService.internalTransfer(owner.principal(), key,
                    new InternalTransferRequest(from, to, new BigDecimal(amount), "concurrency test"));
            return new Outcome(result.body().referenceNumber(), result.replayed(), null);
        } catch (BankException e) {
            return new Outcome(null, false, e.getErrorCode());
        }
    }

    private static List<Outcome> runConcurrently(List<Callable<Outcome>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Outcome>> futures = new ArrayList<>();
            for (Callable<Outcome> task : tasks) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown(); // release every thread at once
            List<Outcome> outcomes = new ArrayList<>();
            for (Future<Outcome> future : futures) {
                outcomes.add(future.get());
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    private BigDecimal balance(Long accountId) {
        return accounts.findById(accountId).map(Account::getBalance).orElseThrow();
    }

    /** The account balance must equal the sum of its successful ledger rows: nothing moved off-book. */
    private void assertLedgerMatchesBalance(Long accountId) {
        Map<TransactionDirection, BigDecimal> totals = transactions
                .findAll(successfulRowsOf(accountId), Pageable.unpaged()).stream()
                .collect(Collectors.groupingBy(Transaction::getDirection,
                        Collectors.mapping(Transaction::getAmount, Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))));
        BigDecimal credits = totals.getOrDefault(TransactionDirection.CREDIT, BigDecimal.ZERO);
        BigDecimal debits = totals.getOrDefault(TransactionDirection.DEBIT, BigDecimal.ZERO);
        assertThat(credits.subtract(debits)).isEqualByComparingTo(balance(accountId));
    }

    private long failedRows(Long accountId) {
        return transactions.count((root, query, cb) -> cb.and(
                cb.equal(root.get("account").get("id"), accountId),
                cb.equal(root.get("status"), TransactionStatus.FAILED)));
    }

    private static Specification<Transaction> successfulRowsOf(Long accountId) {
        return (root, query, cb) -> cb.and(
                cb.equal(root.get("account").get("id"), accountId),
                cb.equal(root.get("status"), TransactionStatus.SUCCESS));
    }

    private record Outcome(String reference, boolean replayed, ErrorCode error) {
        boolean succeeded() {
            return error == null;
        }
    }
}
