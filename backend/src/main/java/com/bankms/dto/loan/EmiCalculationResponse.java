package com.bankms.dto.loan;

import java.math.BigDecimal;
import java.util.List;

public record EmiCalculationResponse(
        BigDecimal principal,
        BigDecimal annualRate,
        int tenureMonths,
        BigDecimal emi,
        BigDecimal totalInterest,
        BigDecimal totalPayment,
        List<ScheduleRow> schedule) {

    public record ScheduleRow(int month, BigDecimal emi, BigDecimal principal, BigDecimal interest, BigDecimal balance) {
    }
}
