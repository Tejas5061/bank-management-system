package com.bankms.dto.customer;

import com.bankms.entity.KycStatus;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record KycDecisionRequest(@NotNull KycStatus status, @Size(max = 500) String remarks) {

    @AssertTrue(message = "status must be VERIFIED or REJECTED")
    public boolean isDecision() {
        return status == null || status != KycStatus.PENDING;
    }

    @AssertTrue(message = "remarks are required when rejecting KYC")
    public boolean isRejectionExplained() {
        return status != KycStatus.REJECTED || (remarks != null && !remarks.isBlank());
    }
}
