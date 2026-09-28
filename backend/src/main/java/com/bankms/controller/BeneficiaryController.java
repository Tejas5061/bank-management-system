package com.bankms.controller;

import com.bankms.dto.beneficiary.BeneficiaryRequest;
import com.bankms.dto.beneficiary.BeneficiaryResponse;
import com.bankms.dto.common.PageResponse;
import com.bankms.security.AuthUser;
import com.bankms.service.BeneficiaryService;
import com.bankms.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Beneficiaries")
@RestController
@RequestMapping("/api/v1/me/beneficiaries")
@PreAuthorize("hasRole('CUSTOMER')")
@RequiredArgsConstructor
public class BeneficiaryController {

    private final BeneficiaryService beneficiaryService;
    private final CustomerService customerService;

    @Operation(summary = "My saved beneficiaries")
    @GetMapping
    public PageResponse<BeneficiaryResponse> list(@AuthenticationPrincipal AuthUser user,
                                                  @ParameterObject @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return beneficiaryService.list(customerService.customerIdForUser(user.id()), pageable);
    }

    @Operation(summary = "Add a beneficiary (transfers open after the cooling period)")
    @PostMapping
    public ResponseEntity<BeneficiaryResponse> add(@AuthenticationPrincipal AuthUser user,
                                                   @Valid @RequestBody BeneficiaryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(beneficiaryService.add(customerService.customerIdForUser(user.id()), request));
    }

    @Operation(summary = "Remove a beneficiary")
    @DeleteMapping("/{beneficiaryId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthUser user, @PathVariable Long beneficiaryId) {
        beneficiaryService.delete(customerService.customerIdForUser(user.id()), beneficiaryId);
        return ResponseEntity.noContent().build();
    }
}
