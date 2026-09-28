package com.bankms.dto.dashboard;

import java.math.BigDecimal;

public record StaffDashboardResponse(
        long pendingKyc,
        long pendingLoans,
        long overdueInstallments,
        long frozenAccounts,
        long todayDepositCount,
        BigDecimal todayDepositAmount,
        long todayWithdrawalCount,
        BigDecimal todayWithdrawalAmount) {
}
