package com.bankms.dto.admin;

import com.bankms.util.ValidationPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record EmployeeUpdateRequest(
        @NotBlank @Pattern(regexp = ValidationPatterns.PHONE, message = "must be a valid 10-digit mobile number") String phone,
        @NotBlank @Pattern(regexp = ValidationPatterns.BRANCH_CODE, message = "must be a 6-digit branch code") String branchCode,
        @NotBlank @Size(max = 60) String designation,
        @NotNull Boolean enabled) {
}
