package com.bankms.controller;

import com.bankms.audit.AuditAction;
import com.bankms.audit.Audited;
import com.bankms.dto.admin.JobRunResponse;
import com.bankms.service.FixedDepositService;
import com.bankms.service.InterestService;
import com.bankms.service.LoanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.YearMonth;

/**
 * Runs the scheduled jobs on demand, for demos and for catching up after downtime. Each job is
 * idempotent, so running one twice does not pay twice.
 */
@Tag(name = "Admin - jobs")
@RestController
@RequestMapping("/api/v1/admin/jobs")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminJobController {

    private final InterestService interestService;
    private final FixedDepositService fixedDepositService;
    private final LoanService loanService;
    private final Clock clock;

    @Operation(summary = "Post savings interest for a completed month (default: last month)")
    @Audited(action = AuditAction.JOB_TRIGGERED, entityType = "JOB", entityId = "'interest'", details = "#period")
    @PostMapping("/interest")
    public JobRunResponse interest(@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth period) {
        return interestService.postMonthlyInterest(period != null ? period : YearMonth.now(clock).minusMonths(1));
    }

    @Operation(summary = "Pay out fixed deposits that have matured")
    @Audited(action = AuditAction.JOB_TRIGGERED, entityType = "JOB", entityId = "'fd-maturity'")
    @PostMapping("/fd-maturity")
    public JobRunResponse fdMaturity() {
        return fixedDepositService.processMaturities();
    }

    @Operation(summary = "Collect due EMIs")
    @Audited(action = AuditAction.JOB_TRIGGERED, entityType = "JOB", entityId = "'emi-debit'")
    @PostMapping("/emi-debit")
    public JobRunResponse emiDebit() {
        return loanService.collectDueInstallments();
    }
}
