package com.bankms.controller;

import com.bankms.dto.common.PageResponse;
import com.bankms.dto.loan.LoanApplicationRequest;
import com.bankms.dto.loan.LoanDetailResponse;
import com.bankms.dto.loan.LoanResponse;
import com.bankms.security.AuthUser;
import com.bankms.service.CustomerService;
import com.bankms.service.LoanService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Loans")
@RestController
@RequestMapping("/api/v1/me/loans")
@PreAuthorize("hasRole('CUSTOMER')")
@RequiredArgsConstructor
public class LoanController {

    private final LoanService loanService;
    private final CustomerService customerService;

    @Operation(summary = "My loans and applications")
    @GetMapping
    public PageResponse<LoanResponse> list(@AuthenticationPrincipal AuthUser user,
                                           @ParameterObject @PageableDefault(size = 20, sort = "appliedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return loanService.listForCustomer(customerService.customerIdForUser(user.id()), pageable);
    }

    @Operation(summary = "Apply for a personal, home or education loan")
    @PostMapping
    public ResponseEntity<LoanResponse> apply(@AuthenticationPrincipal AuthUser user,
                                              @Valid @RequestBody LoanApplicationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(loanService.apply(customerService.customerIdForUser(user.id()), request));
    }

    @Operation(summary = "One of my loans with its EMI schedule")
    @GetMapping("/{loanId}")
    public LoanDetailResponse get(@AuthenticationPrincipal AuthUser user, @PathVariable Long loanId) {
        return loanService.detailForCustomer(customerService.customerIdForUser(user.id()), loanId);
    }
}
