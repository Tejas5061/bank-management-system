package com.bankms.dto.dashboard;

import com.bankms.dto.account.AccountResponse;
import com.bankms.dto.transaction.TransactionResponse;
import com.bankms.entity.KycStatus;
import com.bankms.entity.TransactionType;

import java.math.BigDecimal;
import java.util.List;

public record CustomerDashboardResponse(
        String fullName,
        String customerNumber,
        KycStatus kycStatus,
        BigDecimal totalBalance,
        BigDecimal fixedDepositTotal,
        BigDecimal loanOutstanding,
        int activeLoans,
        long unreadNotifications,
        List<AccountResponse> accounts,
        List<TransactionResponse> recentTransactions,
        List<MonthlyCashflow> monthlyCashflow,
        List<SpendingCategory> spendingByCategory) {

    /** month is yyyy-MM. */
    public record MonthlyCashflow(String month, BigDecimal income, BigDecimal spending) {
    }

    public record SpendingCategory(TransactionType type, long count, BigDecimal amount) {
    }
}
