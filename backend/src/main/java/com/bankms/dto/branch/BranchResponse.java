package com.bankms.dto.branch;

import java.time.Instant;

public record BranchResponse(
        Long id,
        String code,
        String name,
        String ifsc,
        String addressLine,
        String city,
        String state,
        String pincode,
        String phone,
        boolean active,
        Instant createdAt) {
}
