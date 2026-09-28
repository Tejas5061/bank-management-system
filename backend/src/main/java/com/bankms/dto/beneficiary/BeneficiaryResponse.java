package com.bankms.dto.beneficiary;

import java.time.Instant;

/** active=false while the cooling period runs; activatedAt says when transfers open up. */
public record BeneficiaryResponse(
        Long id,
        String name,
        String nickname,
        String maskedAccountNumber,
        String ifsc,
        String bankName,
        boolean internal,
        boolean active,
        Instant activatedAt,
        Instant createdAt) {
}
