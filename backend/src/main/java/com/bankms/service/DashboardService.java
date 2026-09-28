package com.bankms.service;

import com.bankms.dto.account.AccountResponse;
import com.bankms.dto.dashboard.CustomerDashboardResponse;
import com.bankms.dto.dashboard.StaffDashboardResponse;
import com.bankms.entity.AccountStatus;
import com.bankms.entity.AccountType;
import com.bankms.entity.Customer;
import com.bankms.entity.KycStatus;
import com.bankms.entity.LoanStatus;
import com.bankms.entity.TransactionDirection;
import com.bankms.entity.TransactionType;
import com.bankms.exception.BankException;
import com.bankms.mapper.TransactionMapper;
import com.bankms.repository.AccountRepository;
import com.bankms.repository.CustomerRepository;
import com.bankms.repository.LoanInstallmentRepository;
import com.bankms.repository.LoanRepository;
import com.bankms.repository.NotificationRepository;
import com.bankms.repository.TransactionRepository;
import com.bankms.repository.projection.MonthlyFlow;
import com.bankms.repository.projection.TypeTotal;
import com.bankms.util.Money;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final int MONTHS = 6;

    private final CustomerRepository customers;
    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final LoanRepository loans;
    private final LoanInstallmentRepository installments;
    private final NotificationRepository notifications;
    private final AccountService accountService;
    private final TransactionMapper transactionMapper;
    private final Clock clock;

    @Transactional(readOnly = true)
    public CustomerDashboardResponse forCustomer(Long userId) {
        Customer customer = customers.findByUserId(userId)
                .orElseThrow(() -> BankException.notFound("Customer profile for user", userId));
        Long customerId = customer.getId();
        LocalDate today = LocalDate.now(clock);

        List<AccountResponse> accountViews = accountService.listForCustomer(customerId);
        BigDecimal operative = accountViews.stream()
                .filter(a -> a.accountType() != AccountType.FIXED_DEPOSIT && a.status() != AccountStatus.CLOSED)
                .map(AccountResponse::balance).reduce(Money.ZERO, BigDecimal::add);
        BigDecimal deposits = accountViews.stream()
                .filter(a -> a.accountType() == AccountType.FIXED_DEPOSIT && a.status() != AccountStatus.CLOSED)
                .map(AccountResponse::balance).reduce(Money.ZERO, BigDecimal::add);

        var activeLoans = loans.findByCustomerId(customerId, Pageable.unpaged()).stream()
                .filter(l -> l.getStatus() == LoanStatus.ACTIVE).toList();
        BigDecimal loanOutstanding = activeLoans.stream()
                .map(l -> l.getOutstandingPrincipal()).reduce(Money.ZERO, BigDecimal::add);

        YearMonth firstMonth = YearMonth.from(today).minusMonths(MONTHS - 1L);
        Map<String, List<MonthlyFlow>> flowByMonth = transactions
                .monthlyFlowForCustomer(customerId, firstMonth.atDay(1)).stream()
                .collect(Collectors.groupingBy(f -> YearMonth.of(f.getYear(), f.getMonth()).toString()));
        List<CustomerDashboardResponse.MonthlyCashflow> cashflow = new ArrayList<>();
        for (int i = 0; i < MONTHS; i++) {
            String month = firstMonth.plusMonths(i).toString();
            List<MonthlyFlow> rows = flowByMonth.getOrDefault(month, List.of());
            cashflow.add(new CustomerDashboardResponse.MonthlyCashflow(month,
                    total(rows, TransactionDirection.CREDIT), total(rows, TransactionDirection.DEBIT)));
        }

        List<CustomerDashboardResponse.SpendingCategory> spending = transactions
                .spendingByTypeForCustomer(customerId, today.minusDays(29)).stream()
                .sorted(Comparator.comparing(TypeTotal::getTotal).reversed())
                .map(t -> new CustomerDashboardResponse.SpendingCategory(t.getType(), t.getCount(), t.getTotal()))
                .toList();

        return new CustomerDashboardResponse(
                customer.getUser().getFullName(),
                customer.getCustomerNumber(),
                customer.getKycStatus(),
                operative,
                deposits,
                loanOutstanding,
                activeLoans.size(),
                notifications.countByUserIdAndReadFalse(userId),
                accountViews,
                transactionMapper.toResponses(transactions.findRecentForCustomer(customerId, PageRequest.of(0, 8))),
                cashflow,
                spending);
    }

    @Transactional(readOnly = true)
    public StaffDashboardResponse forStaff() {
        Map<TransactionType, TypeTotal> todayByType = transactions.totalsByTypeOn(LocalDate.now(clock)).stream()
                .collect(Collectors.toMap(TypeTotal::getType, Function.identity()));
        TypeTotal deposits = todayByType.get(TransactionType.DEPOSIT);
        TypeTotal withdrawals = todayByType.get(TransactionType.WITHDRAWAL);
        return new StaffDashboardResponse(
                customers.countByKycStatus(KycStatus.PENDING),
                loans.countByStatus(LoanStatus.PENDING),
                installments.countOverdue(),
                accounts.countByStatus(AccountStatus.FROZEN),
                deposits == null ? 0 : deposits.getCount(),
                deposits == null ? Money.ZERO : deposits.getTotal(),
                withdrawals == null ? 0 : withdrawals.getCount(),
                withdrawals == null ? Money.ZERO : withdrawals.getTotal());
    }

    private static BigDecimal total(List<MonthlyFlow> rows, TransactionDirection direction) {
        return rows.stream().filter(r -> r.getDirection() == direction)
                .map(MonthlyFlow::getTotal).reduce(Money.ZERO, BigDecimal::add);
    }
}
