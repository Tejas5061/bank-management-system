package com.bankms.controller;

import com.bankms.dto.account.AccountResponse;
import com.bankms.dto.account.OpenAccountRequest;
import com.bankms.dto.common.PageResponse;
import com.bankms.dto.transaction.TransactionResponse;
import com.bankms.entity.TransactionStatus;
import com.bankms.entity.TransactionType;
import com.bankms.security.AuthUser;
import com.bankms.service.AccountService;
import com.bankms.service.CustomerService;
import com.bankms.service.TransactionHistoryService;
import com.bankms.service.statement.StatementFile;
import com.bankms.service.statement.StatementFormat;
import com.bankms.service.statement.StatementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "Accounts")
@RestController
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;
    private final CustomerService customerService;
    private final TransactionHistoryService historyService;
    private final StatementService statementService;

    @Operation(summary = "My accounts")
    @PreAuthorize("hasRole('CUSTOMER')")
    @GetMapping("/api/v1/me/accounts")
    public List<AccountResponse> myAccounts(@AuthenticationPrincipal AuthUser user) {
        return accountService.listForCustomer(customerService.customerIdForUser(user.id()));
    }

    @Operation(summary = "Open a savings or current account (KYC must be verified)")
    @PreAuthorize("hasRole('CUSTOMER')")
    @PostMapping("/api/v1/me/accounts")
    public ResponseEntity<AccountResponse> open(@AuthenticationPrincipal AuthUser user,
                                                @Valid @RequestBody OpenAccountRequest request) {
        AccountResponse account = accountService.open(customerService.customerIdForUser(user.id()), request.accountType());
        return ResponseEntity.status(HttpStatus.CREATED).body(account);
    }

    @Operation(summary = "Account details (owner or staff)")
    @PreAuthorize("@accountSecurity.canView(#accountId)")
    @GetMapping("/api/v1/accounts/{accountId}")
    public AccountResponse get(@PathVariable Long accountId) {
        return accountService.get(accountId);
    }

    @Operation(summary = "Paginated history with optional date-range, type and status filters (owner or staff)")
    @PreAuthorize("@accountSecurity.canView(#accountId)")
    @GetMapping("/api/v1/accounts/{accountId}/transactions")
    public PageResponse<TransactionResponse> transactions(
            @PathVariable Long accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) TransactionStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return historyService.history(accountId, from, to, type, status, pageable);
    }

    @Operation(summary = "Download a statement as PDF or CSV (defaults to the last 30 days)")
    @PreAuthorize("@accountSecurity.canView(#accountId)")
    @GetMapping("/api/v1/accounts/{accountId}/statement")
    public ResponseEntity<byte[]> statement(
            @PathVariable Long accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "PDF") StatementFormat format) {
        StatementFile file = statementService.generate(accountId, from, to, format);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.filename()).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(file.content());
    }
}
