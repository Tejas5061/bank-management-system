package com.bankms.controller;

import com.bankms.dto.customer.CustomerProfileResponse;
import com.bankms.dto.customer.UpdateProfileRequest;
import com.bankms.dto.dashboard.CustomerDashboardResponse;
import com.bankms.security.AuthUser;
import com.bankms.service.CustomerService;
import com.bankms.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Everything under /me is scoped to the caller's own customer record; no ids to tamper with. */
@Tag(name = "Customer")
@RestController
@RequestMapping("/api/v1/me")
@PreAuthorize("hasRole('CUSTOMER')")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;
    private final DashboardService dashboardService;

    @Operation(summary = "My profile (PAN and Aadhaar masked)")
    @GetMapping("/profile")
    public CustomerProfileResponse profile(@AuthenticationPrincipal AuthUser user) {
        return customerService.profileForUser(user.id());
    }

    @Operation(summary = "Update my contact details")
    @PutMapping("/profile")
    public CustomerProfileResponse updateProfile(@AuthenticationPrincipal AuthUser user,
                                                 @Valid @RequestBody UpdateProfileRequest request) {
        return customerService.updateProfile(user.id(), request);
    }

    @Operation(summary = "Balances, recent activity, 6-month cash flow and 30-day spending")
    @GetMapping("/dashboard")
    public CustomerDashboardResponse dashboard(@AuthenticationPrincipal AuthUser user) {
        return dashboardService.forCustomer(user.id());
    }
}
