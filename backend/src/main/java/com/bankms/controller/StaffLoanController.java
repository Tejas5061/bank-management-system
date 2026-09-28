package com.bankms.controller;

import com.bankms.dto.common.PageResponse;
import com.bankms.dto.loan.LoanDecisionRequest;
import com.bankms.dto.loan.LoanDetailResponse;
import com.bankms.dto.loan.LoanResponse;
import com.bankms.entity.LoanStatus;
import com.bankms.security.AuthUser;
import com.bankms.service.LoanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Staff - loans")
@RestController
@RequestMapping("/api/v1/staff/loans")
@PreAuthorize("hasAnyRole('EMPLOYEE', 'ADMIN')")
@RequiredArgsConstructor
public class StaffLoanController {

    private final LoanService loanService;

    @Operation(summary = "Loan applications and loans, optionally by status")
    @GetMapping
    public PageResponse<LoanResponse> list(
            @RequestParam(required = false) LoanStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "appliedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return loanService.list(status, pageable);
    }

    @Operation(summary = "Loan with its EMI schedule")
    @GetMapping("/{loanId}")
    public LoanDetailResponse get(@PathVariable Long loanId) {
        return loanService.detail(loanId);
    }

    @Operation(summary = "Approve and disburse (generates the EMI schedule)")
    @PreAuthorize("hasRole('EMPLOYEE')")
    @PostMapping("/{loanId}/approve")
    public LoanDetailResponse approve(@AuthenticationPrincipal AuthUser user, @PathVariable Long loanId,
                                      @Valid @RequestBody LoanDecisionRequest request) {
        return loanService.approve(loanId, user.id(), request);
    }

    @Operation(summary = "Reject with remarks")
    @PreAuthorize("hasRole('EMPLOYEE')")
    @PostMapping("/{loanId}/reject")
    public LoanDetailResponse reject(@AuthenticationPrincipal AuthUser user, @PathVariable Long loanId,
                                     @Valid @RequestBody LoanDecisionRequest request) {
        return loanService.reject(loanId, user.id(), request);
    }
}
