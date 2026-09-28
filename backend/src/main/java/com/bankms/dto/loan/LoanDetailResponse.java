package com.bankms.dto.loan;

import java.util.List;

public record LoanDetailResponse(LoanResponse loan, List<InstallmentResponse> schedule, int paidInstallments) {
}
