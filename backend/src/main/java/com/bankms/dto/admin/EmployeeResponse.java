package com.bankms.dto.admin;

import java.time.Instant;

public record EmployeeResponse(
        Long id,
        Long userId,
        String employeeCode,
        String fullName,
        String email,
        String phone,
        String designation,
        String branchCode,
        String branchName,
        boolean enabled,
        boolean locked,
        Instant lastLoginAt,
        Instant createdAt) {
}
