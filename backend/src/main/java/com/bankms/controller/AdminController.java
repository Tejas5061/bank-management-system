package com.bankms.controller;

import com.bankms.audit.AuditAction;
import com.bankms.audit.AuditOutcome;
import com.bankms.dto.admin.AccountPolicyRequest;
import com.bankms.dto.admin.AccountPolicyResponse;
import com.bankms.dto.admin.AnalyticsOverviewResponse;
import com.bankms.dto.admin.AuditLogResponse;
import com.bankms.dto.admin.DailyVolumePoint;
import com.bankms.dto.admin.EmployeeRequest;
import com.bankms.dto.admin.EmployeeResponse;
import com.bankms.dto.admin.EmployeeUpdateRequest;
import com.bankms.dto.admin.LoanProductRequest;
import com.bankms.dto.admin.LoanProductResponse;
import com.bankms.dto.admin.RateCardResponse;
import com.bankms.dto.branch.BranchRequest;
import com.bankms.dto.branch.BranchResponse;
import com.bankms.dto.common.MessageResponse;
import com.bankms.dto.common.PageResponse;
import com.bankms.entity.AccountType;
import com.bankms.entity.LoanType;
import com.bankms.security.AuthUser;
import com.bankms.service.AnalyticsService;
import com.bankms.service.AuditService;
import com.bankms.service.BranchService;
import com.bankms.service.EmployeeService;
import com.bankms.service.RateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@Tag(name = "Admin")
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final EmployeeService employeeService;
    private final BranchService branchService;
    private final RateService rateService;
    private final AnalyticsService analyticsService;
    private final AuditService auditService;

    // ----- analytics -----

    @Operation(summary = "Deposits, accounts, KYC backlog, today's volume and loan portfolio")
    @GetMapping("/analytics/overview")
    public AnalyticsOverviewResponse overview() {
        return analyticsService.overview();
    }

    @Operation(summary = "Daily credit/debit volume for the last N days")
    @GetMapping("/analytics/daily-volume")
    public List<DailyVolumePoint> dailyVolume(@RequestParam(defaultValue = "30") @Min(1) @Max(365) int days) {
        return analyticsService.dailyVolume(days);
    }

    // ----- employees -----

    @Operation(summary = "Search employees")
    @GetMapping("/employees")
    public PageResponse<EmployeeResponse> employees(@RequestParam(required = false) String query,
                                                    @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return employeeService.search(query, pageable);
    }

    @Operation(summary = "Create an employee (temporary password emailed)")
    @PostMapping("/employees")
    public ResponseEntity<EmployeeResponse> createEmployee(@Valid @RequestBody EmployeeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(employeeService.create(request));
    }

    @GetMapping("/employees/{employeeId}")
    public EmployeeResponse employee(@PathVariable Long employeeId) {
        return employeeService.get(employeeId);
    }

    @Operation(summary = "Update branch/designation, or enable/disable an employee")
    @PutMapping("/employees/{employeeId}")
    public EmployeeResponse updateEmployee(@AuthenticationPrincipal AuthUser admin, @PathVariable Long employeeId,
                                           @Valid @RequestBody EmployeeUpdateRequest request) {
        return employeeService.update(employeeId, request, admin.id());
    }

    @Operation(summary = "Unlock a user locked out by failed logins")
    @PostMapping("/users/{userId}/unlock")
    public MessageResponse unlock(@PathVariable Long userId) {
        employeeService.unlockUser(userId);
        return new MessageResponse("User unlocked");
    }

    // ----- branches -----

    @GetMapping("/branches")
    public PageResponse<BranchResponse> branches(@ParameterObject @PageableDefault(size = 20, sort = "code") Pageable pageable) {
        return branchService.list(pageable);
    }

    @PostMapping("/branches")
    public ResponseEntity<BranchResponse> createBranch(@Valid @RequestBody BranchRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(branchService.create(request));
    }

    @PutMapping("/branches/{branchId}")
    public BranchResponse updateBranch(@PathVariable Long branchId, @Valid @RequestBody BranchRequest request) {
        return branchService.update(branchId, request);
    }

    // ----- rates -----

    @GetMapping("/rates")
    public RateCardResponse rates() {
        return rateService.rateCard();
    }

    @Operation(summary = "Set interest rate, minimum balance and daily transfer limit for an account type")
    @PutMapping("/rates/accounts/{accountType}")
    public AccountPolicyResponse updateAccountPolicy(@AuthenticationPrincipal AuthUser admin,
                                                     @PathVariable AccountType accountType,
                                                     @Valid @RequestBody AccountPolicyRequest request) {
        return rateService.updatePolicy(accountType, request, admin.id());
    }

    @Operation(summary = "Set interest rate and limits for a loan product")
    @PutMapping("/rates/loans/{loanType}")
    public LoanProductResponse updateLoanProduct(@AuthenticationPrincipal AuthUser admin,
                                                 @PathVariable LoanType loanType,
                                                 @Valid @RequestBody LoanProductRequest request) {
        return rateService.updateLoanProduct(loanType, request, admin.id());
    }

    // ----- audit -----

    @Operation(summary = "Search the audit trail")
    @GetMapping("/audit-logs")
    public PageResponse<AuditLogResponse> auditLogs(
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) AuditOutcome outcome,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @ParameterObject @PageableDefault(size = 25, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return auditService.search(action, outcome, actor, from, to, pageable);
    }
}
