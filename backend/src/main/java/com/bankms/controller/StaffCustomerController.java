package com.bankms.controller;

import com.bankms.dto.account.AccountResponse;
import com.bankms.dto.account.OpenAccountRequest;
import com.bankms.dto.auth.RegistrationResponse;
import com.bankms.dto.common.PageResponse;
import com.bankms.dto.customer.CustomerDetailResponse;
import com.bankms.dto.customer.CustomerProfileResponse;
import com.bankms.dto.customer.CustomerSummaryResponse;
import com.bankms.dto.customer.KycDecisionRequest;
import com.bankms.dto.customer.OnboardCustomerRequest;
import com.bankms.entity.KycStatus;
import com.bankms.security.AuthUser;
import com.bankms.service.AccountService;
import com.bankms.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Staff - customers & KYC")
@RestController
@RequestMapping("/api/v1/staff/customers")
@PreAuthorize("hasAnyRole('EMPLOYEE', 'ADMIN')")
@RequiredArgsConstructor
public class StaffCustomerController {

    private final CustomerService customerService;
    private final AccountService accountService;

    @Operation(summary = "Search customers by name, email, phone or customer ID; filter by KYC status")
    @GetMapping
    public PageResponse<CustomerSummaryResponse> search(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) KycStatus kycStatus,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return customerService.search(query, kycStatus, pageable);
    }

    @Operation(summary = "Onboard a customer at the branch (temporary password emailed)")
    @PreAuthorize("hasRole('EMPLOYEE')")
    @PostMapping
    public ResponseEntity<RegistrationResponse> onboard(@AuthenticationPrincipal AuthUser user,
                                                        @Valid @RequestBody OnboardCustomerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(customerService.onboard(request, user.id()));
    }

    @Operation(summary = "Customer profile with all accounts")
    @GetMapping("/{customerId}")
    public CustomerDetailResponse detail(@PathVariable Long customerId) {
        return customerService.detail(customerId);
    }

    @Operation(summary = "Verify or reject KYC")
    @PreAuthorize("hasRole('EMPLOYEE')")
    @PatchMapping("/{customerId}/kyc")
    public CustomerProfileResponse reviewKyc(@AuthenticationPrincipal AuthUser user, @PathVariable Long customerId,
                                             @Valid @RequestBody KycDecisionRequest request) {
        return customerService.reviewKyc(customerId, request, user.id());
    }

    @Operation(summary = "Open another savings/current account for the customer")
    @PreAuthorize("hasRole('EMPLOYEE')")
    @PostMapping("/{customerId}/accounts")
    public ResponseEntity<AccountResponse> openAccount(@PathVariable Long customerId,
                                                       @Valid @RequestBody OpenAccountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(accountService.open(customerId, request.accountType()));
    }
}
