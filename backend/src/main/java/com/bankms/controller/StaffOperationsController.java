package com.bankms.controller;

import com.bankms.dto.account.AccountLookupResponse;
import com.bankms.dto.account.AccountResponse;
import com.bankms.dto.account.AccountStatusRequest;
import com.bankms.dto.dashboard.StaffDashboardResponse;
import com.bankms.dto.transaction.CashRequest;
import com.bankms.dto.transaction.CashResponse;
import com.bankms.security.AuthUser;
import com.bankms.service.AccountService;
import com.bankms.service.DashboardService;
import com.bankms.service.TransactionService;
import com.bankms.util.ValidationPatterns;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Staff - cash desk & accounts")
@RestController
@RequestMapping("/api/v1/staff")
@PreAuthorize("hasAnyRole('EMPLOYEE', 'ADMIN')")
@RequiredArgsConstructor
public class StaffOperationsController {

    private final DashboardService dashboardService;
    private final AccountService accountService;
    private final TransactionService transactionService;

    @Operation(summary = "Work queues and today's cash totals")
    @GetMapping("/dashboard")
    public StaffDashboardResponse dashboard() {
        return dashboardService.forStaff();
    }

    @Operation(summary = "Look up an account by number (holder, KYC, balance)")
    @GetMapping("/accounts/lookup")
    public AccountLookupResponse lookup(
            @RequestParam @Pattern(regexp = ValidationPatterns.ACCOUNT_NUMBER, message = "must be 9-18 digits") String accountNumber) {
        return accountService.lookup(accountNumber);
    }

    @Operation(summary = "Freeze, unfreeze or close an account")
    @PatchMapping("/accounts/{accountId}/status")
    public AccountResponse changeStatus(@PathVariable Long accountId, @Valid @RequestBody AccountStatusRequest request) {
        return accountService.changeStatus(accountId, request);
    }

    /** Cash handling is a teller duty: admins can look, but not move cash. */
    @Operation(summary = "Cash deposit at the counter")
    @PreAuthorize("hasRole('EMPLOYEE')")
    @PostMapping("/cash/deposits")
    public ResponseEntity<CashResponse> deposit(
            @AuthenticationPrincipal AuthUser user,
            @RequestHeader(Responses.IDEMPOTENCY_KEY)
            @Pattern(regexp = ValidationPatterns.IDEMPOTENCY_KEY, message = "must be 8-80 letters, digits, '-' or '_'") String idempotencyKey,
            @Valid @RequestBody CashRequest request) {
        return Responses.created(transactionService.cashDeposit(user, idempotencyKey, request));
    }

    @Operation(summary = "Cash withdrawal at the counter")
    @PreAuthorize("hasRole('EMPLOYEE')")
    @PostMapping("/cash/withdrawals")
    public ResponseEntity<CashResponse> withdraw(
            @AuthenticationPrincipal AuthUser user,
            @RequestHeader(Responses.IDEMPOTENCY_KEY)
            @Pattern(regexp = ValidationPatterns.IDEMPOTENCY_KEY, message = "must be 8-80 letters, digits, '-' or '_'") String idempotencyKey,
            @Valid @RequestBody CashRequest request) {
        return Responses.created(transactionService.cashWithdrawal(user, idempotencyKey, request));
    }
}
