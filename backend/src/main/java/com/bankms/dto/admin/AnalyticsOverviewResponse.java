package com.bankms.dto.admin;

import com.bankms.entity.AccountType;
import com.bankms.entity.LoanType;

import java.math.BigDecimal;
import java.util.List;

public record AnalyticsOverviewResponse(
        BigDecimal totalDeposits,
        List<DepositByType> depositsByType,
        long activeAccounts,
        long frozenAccounts,
        long totalCustomers,
        long pendingKyc,
        long todayTransactionCount,
        BigDecimal todayTransactionVolume,
        LoanPortfolio loanPortfolio) {

    public record DepositByType(AccountType accountType, long accounts, BigDecimal balance) {
    }

    public record LoanPortfolio(
            long activeLoans,
            long pendingApplications,
            long closedLoans,
            long overdueInstallments,
            BigDecimal totalDisbursed,
            BigDecimal totalOutstanding,
            List<LoanTypeSummary> byType) {
    }

    public record LoanTypeSummary(LoanType loanType, long activeLoans, BigDecimal outstanding, long pending) {
    }
}
