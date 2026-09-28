package com.bankms.dto.customer;

import com.bankms.entity.KycStatus;

import java.time.Instant;

public record CustomerSummaryResponse(
        Long id,
        String customerNumber,
        String fullName,
        String email,
        String phone,
        KycStatus kycStatus,
        String homeBranchCode,
        Instant createdAt) {
}
