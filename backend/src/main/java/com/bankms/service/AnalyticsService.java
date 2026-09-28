package com.bankms.service;

import com.bankms.dto.admin.AnalyticsOverviewResponse;
import com.bankms.dto.admin.DailyVolumePoint;
import com.bankms.entity.AccountStatus;
import com.bankms.entity.KycStatus;
import com.bankms.entity.LoanStatus;
import com.bankms.entity.LoanType;
import com.bankms.entity.TransactionDirection;
import com.bankms.repository.AccountRepository;
import com.bankms.repository.CustomerRepository;
import com.bankms.repository.LoanInstallmentRepository;
import com.bankms.repository.LoanRepository;
import com.bankms.repository.TransactionRepository;
import com.bankms.repository.projection.DailyVolume;
import com.bankms.repository.projection.LoanPortfolioRow;
import com.bankms.repository.projection.TypeTotal;
import com.bankms.util.Money;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** System-wide numbers for the admin dashboard, computed with aggregate queries (no entity loading). */
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final AccountRepository accounts;
    private final CustomerRepository customers;
    private final TransactionRepository transactions;
    private final LoanRepository loans;
    private final LoanInstallmentRepository installments;
    private final Clock clock;

    @Transactional(readOnly = true)
    public AnalyticsOverviewResponse overview() {
        List<AnalyticsOverviewResponse.DepositByType> deposits = accounts.summarizeDeposits().stream()
                .map(d -> new AnalyticsOverviewResponse.DepositByType(d.getAccountType(), d.getAccounts(), d.getBalance()))
                .toList();
        BigDecimal totalDeposits = deposits.stream()
                .map(AnalyticsOverviewResponse.DepositByType::balance).reduce(Money.ZERO, BigDecimal::add);

        List<TypeTotal> today = transactions.totalsByTypeOn(LocalDate.now(clock));
        long todayCount = today.stream().mapToLong(TypeTotal::getCount).sum();
        BigDecimal todayVolume = today.stream().map(TypeTotal::getTotal).reduce(Money.ZERO, BigDecimal::add);

        return new AnalyticsOverviewResponse(
                totalDeposits,
                deposits,
                accounts.countByStatus(AccountStatus.ACTIVE),
                accounts.countByStatus(AccountStatus.FROZEN),
                customers.count(),
                customers.countByKycStatus(KycStatus.PENDING),
                todayCount,
                todayVolume,
                loanPortfolio());
    }

    /** One point per day, including days with no activity, so the chart has no gaps. */
    @Transactional(readOnly = true)
    public List<DailyVolumePoint> dailyVolume(int days) {
        LocalDate end = LocalDate.now(clock);
        LocalDate start = end.minusDays(days - 1L);
        Map<LocalDate, List<DailyVolume>> byDay = transactions.dailyVolumeSince(start).stream()
                .collect(Collectors.groupingBy(DailyVolume::getValueDate));
        List<DailyVolumePoint> points = new ArrayList<>(days);
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            List<DailyVolume> rows = byDay.getOrDefault(day, List.of());
            BigDecimal credits = Money.ZERO;
            BigDecimal debits = Money.ZERO;
            long count = 0;
            for (DailyVolume row : rows) {
                count += row.getCount();
                if (row.getDirection() == TransactionDirection.CREDIT) {
                    credits = credits.add(row.getTotal());
                } else {
                    debits = debits.add(row.getTotal());
                }
            }
            points.add(new DailyVolumePoint(day, credits, debits, count));
        }
        return points;
    }

    private AnalyticsOverviewResponse.LoanPortfolio loanPortfolio() {
        List<LoanPortfolioRow> rows = loans.portfolio();
        long active = count(rows, LoanStatus.ACTIVE);
        long pending = count(rows, LoanStatus.PENDING);
        long closed = count(rows, LoanStatus.CLOSED);
        BigDecimal disbursed = rows.stream()
                .filter(r -> r.getStatus() == LoanStatus.ACTIVE || r.getStatus() == LoanStatus.CLOSED)
                .map(LoanPortfolioRow::getPrincipal).reduce(Money.ZERO, BigDecimal::add);
        BigDecimal outstanding = rows.stream()
                .filter(r -> r.getStatus() == LoanStatus.ACTIVE)
                .map(LoanPortfolioRow::getOutstanding).reduce(Money.ZERO, BigDecimal::add);
        List<AnalyticsOverviewResponse.LoanTypeSummary> byType = Arrays.stream(LoanType.values())
                .map(type -> {
                    List<LoanPortfolioRow> ofType = rows.stream().filter(r -> r.getLoanType() == type).toList();
                    return new AnalyticsOverviewResponse.LoanTypeSummary(type,
                            count(ofType, LoanStatus.ACTIVE),
                            ofType.stream().filter(r -> r.getStatus() == LoanStatus.ACTIVE)
                                    .map(LoanPortfolioRow::getOutstanding).reduce(Money.ZERO, BigDecimal::add),
                            count(ofType, LoanStatus.PENDING));
                })
                .toList();
        return new AnalyticsOverviewResponse.LoanPortfolio(active, pending, closed, installments.countOverdue(),
                disbursed, outstanding, byType);
    }

    private static long count(List<LoanPortfolioRow> rows, LoanStatus status) {
        return rows.stream().filter(r -> r.getStatus() == status).mapToLong(LoanPortfolioRow::getLoans).sum();
    }
}
