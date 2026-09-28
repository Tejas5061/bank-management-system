package com.bankms.service.ledger;

import com.bankms.entity.Account;
import com.bankms.entity.AccountPolicy;
import com.bankms.entity.AccountStatus;
import com.bankms.entity.AccountType;
import com.bankms.entity.Customer;
import com.bankms.entity.KycStatus;
import com.bankms.entity.TransactionType;
import com.bankms.exception.ErrorCode;
import com.bankms.exception.TransactionDeclinedException;
import com.bankms.repository.TransactionRepository;
import com.bankms.service.RateService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LedgerServiceTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-28T06:00:00Z"), IST);

    @Mock
    private EntityManager entityManager;
    @Mock
    private TransactionRepository transactions;
    @Mock
    private RateService rates;

    private LedgerService ledger;

    @BeforeEach
    void setUp() {
        ledger = new LedgerService(entityManager, transactions, rates, CLOCK);
        AccountPolicy savings = new AccountPolicy();
        savings.setAccountType(AccountType.SAVINGS);
        savings.setMinimumBalance(new BigDecimal("1000.00"));
        savings.setDailyTransferLimit(new BigDecimal("200000.00"));
        savings.setInterestRate(new BigDecimal("3.50"));
        lenient().when(rates.policy(AccountType.SAVINGS)).thenReturn(savings);
    }

    @Test
    void locksAccountsInAscendingIdOrderWhateverTheArgumentOrder() {
        Account nine = mock(Account.class);
        Account three = mock(Account.class);
        when(entityManager.getReference(Account.class, 9L)).thenReturn(nine);
        when(entityManager.getReference(Account.class, 3L)).thenReturn(three);

        var locked = ledger.lockInOrder(9L, 3L);

        InOrder order = inOrder(entityManager);
        order.verify(entityManager).refresh(three, LockModeType.PESSIMISTIC_WRITE);
        order.verify(entityManager).refresh(nine, LockModeType.PESSIMISTIC_WRITE);
        assertThat(locked).containsOnlyKeys(3L, 9L);
    }

    @Test
    void frozenAccountCannotTransact() {
        Account account = account("5000.00", KycStatus.VERIFIED, AccountStatus.FROZEN);
        assertThatThrownBy(() -> ledger.requireCanTransact(account, TransactionType.TRANSFER_OUT, new BigDecimal("10")))
                .isInstanceOf(TransactionDeclinedException.class)
                .extracting(e -> ((TransactionDeclinedException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCOUNT_FROZEN);
    }

    @Test
    void accountCannotTransactUntilKycIsVerified() {
        Account account = account("5000.00", KycStatus.PENDING, AccountStatus.ACTIVE);
        assertThatThrownBy(() -> ledger.requireCanTransact(account, TransactionType.WITHDRAWAL, new BigDecimal("10")))
                .extracting(e -> ((TransactionDeclinedException) e).getErrorCode())
                .isEqualTo(ErrorCode.KYC_NOT_VERIFIED);
    }

    @Test
    void minimumBalanceIsKeptAside() {
        Account account = account("5000.00", KycStatus.VERIFIED, AccountStatus.ACTIVE);
        ledger.requireSufficientFunds(account, new BigDecimal("4000.00"), TransactionType.TRANSFER_OUT, true);
        assertThatThrownBy(() -> ledger.requireSufficientFunds(account, new BigDecimal("4000.01"), TransactionType.TRANSFER_OUT, true))
                .isInstanceOf(TransactionDeclinedException.class)
                .hasMessageContaining("minimum balance")
                .extracting(e -> ((TransactionDeclinedException) e).getBalanceAtDecline())
                .isEqualTo(new BigDecimal("5000.00"));
    }

    @Test
    void bankInitiatedDebitsMayDipBelowTheMinimum() {
        Account account = account("5000.00", KycStatus.VERIFIED, AccountStatus.ACTIVE);
        ledger.requireSufficientFunds(account, new BigDecimal("5000.00"), TransactionType.EMI_DEBIT, false);
    }

    @Test
    void dailyLimitCountsEverythingAlreadySentToday() {
        Account account = account("500000.00", KycStatus.VERIFIED, AccountStatus.ACTIVE);
        when(transactions.sumSuccessfulByTypeOnDate(eq(1L), eq(TransactionType.TRANSFER_OUT), any(LocalDate.class)))
                .thenReturn(new BigDecimal("150000.00"));

        ledger.requireWithinDailyLimit(account, new BigDecimal("50000.00"));
        assertThatThrownBy(() -> ledger.requireWithinDailyLimit(account, new BigDecimal("50000.01")))
                .extracting(e -> ((TransactionDeclinedException) e).getErrorCode())
                .isEqualTo(ErrorCode.DAILY_LIMIT_EXCEEDED);
    }

    @Test
    void businessDateComesFromTheBankTimeZone() {
        // 2026-09-28T20:00Z is already the 29th in India
        LedgerService late = new LedgerService(entityManager, transactions, rates,
                Clock.fixed(Instant.parse("2026-09-28T20:00:00Z"), IST));
        assertThat(late.today()).isEqualTo(LocalDate.of(2026, 9, 29));
    }

    private static Account account(String balance, KycStatus kyc, AccountStatus status) {
        Customer customer = new Customer();
        customer.setKycStatus(kyc);
        Account account = new Account() {
            @Override
            public Long getId() {
                return 1L;
            }
        };
        account.setCustomer(customer);
        account.setAccountNumber("000110000015");
        account.setAccountType(AccountType.SAVINGS);
        account.setStatus(status);
        account.setBalance(new BigDecimal(balance));
        return account;
    }
}
