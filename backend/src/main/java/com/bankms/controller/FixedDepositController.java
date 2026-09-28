package com.bankms.controller;

import com.bankms.dto.common.PageResponse;
import com.bankms.dto.deposit.FixedDepositRequest;
import com.bankms.dto.deposit.FixedDepositResponse;
import com.bankms.security.AuthUser;
import com.bankms.service.CustomerService;
import com.bankms.service.FixedDepositService;
import com.bankms.service.TransactionService;
import com.bankms.util.ValidationPatterns;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Fixed deposits")
@RestController
@RequestMapping("/api/v1/me/fixed-deposits")
@PreAuthorize("hasRole('CUSTOMER')")
@RequiredArgsConstructor
public class FixedDepositController {

    private final FixedDepositService fixedDepositService;
    private final TransactionService transactionService;
    private final CustomerService customerService;

    @Operation(summary = "My fixed deposits")
    @GetMapping
    public PageResponse<FixedDepositResponse> list(@AuthenticationPrincipal AuthUser user,
                                                   @ParameterObject @PageableDefault(size = 20, sort = "startDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return fixedDepositService.listForCustomer(customerService.customerIdForUser(user.id()), pageable);
    }

    @Operation(summary = "Book a fixed deposit funded from one of my accounts")
    @PostMapping
    public ResponseEntity<FixedDepositResponse> open(
            @AuthenticationPrincipal AuthUser user,
            @RequestHeader(Responses.IDEMPOTENCY_KEY)
            @Pattern(regexp = ValidationPatterns.IDEMPOTENCY_KEY, message = "must be 8-80 letters, digits, '-' or '_'") String idempotencyKey,
            @Valid @RequestBody FixedDepositRequest request) {
        return Responses.created(transactionService.openFixedDeposit(user, idempotencyKey, request));
    }
}
