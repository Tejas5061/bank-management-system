package com.bankms.controller;

import com.bankms.dto.transaction.BeneficiaryTransferRequest;
import com.bankms.dto.transaction.InternalTransferRequest;
import com.bankms.dto.transaction.TransferResponse;
import com.bankms.security.AuthUser;
import com.bankms.service.TransactionService;
import com.bankms.util.ValidationPatterns;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Transfers")
@RestController
@RequestMapping("/api/v1/transfers")
@PreAuthorize("hasRole('CUSTOMER')")
@RequiredArgsConstructor
public class TransferController {

    private static final String KEY_DOC = "Unique per logical transfer (e.g. a UUID). Retrying with the same key never transfers twice.";

    private final TransactionService transactionService;

    @Operation(summary = "Move money between two of my accounts")
    @PostMapping("/internal")
    public ResponseEntity<TransferResponse> internal(
            @AuthenticationPrincipal AuthUser user,
            @Parameter(description = KEY_DOC) @RequestHeader(Responses.IDEMPOTENCY_KEY)
            @Pattern(regexp = ValidationPatterns.IDEMPOTENCY_KEY, message = "must be 8-80 letters, digits, '-' or '_'") String idempotencyKey,
            @Valid @RequestBody InternalTransferRequest request) {
        return Responses.created(transactionService.internalTransfer(user, idempotencyKey, request));
    }

    @Operation(summary = "Pay a saved beneficiary (after its cooling period)")
    @PostMapping("/beneficiary")
    public ResponseEntity<TransferResponse> toBeneficiary(
            @AuthenticationPrincipal AuthUser user,
            @Parameter(description = KEY_DOC) @RequestHeader(Responses.IDEMPOTENCY_KEY)
            @Pattern(regexp = ValidationPatterns.IDEMPOTENCY_KEY, message = "must be 8-80 letters, digits, '-' or '_'") String idempotencyKey,
            @Valid @RequestBody BeneficiaryTransferRequest request) {
        return Responses.created(transactionService.beneficiaryTransfer(user, idempotencyKey, request));
    }
}
