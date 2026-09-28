package com.bankms.dto.admin;

import com.bankms.util.ValidationPatterns;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** A temporary password is generated and emailed; the employee changes it after first login. */
public record EmployeeRequest(
        @NotBlank @Size(min = 3, max = 100) String fullName,
        @NotBlank @Email @Size(max = 120) String email,
        @NotBlank @Pattern(regexp = ValidationPatterns.PHONE, message = "must be a valid 10-digit mobile number") String phone,
        @NotBlank @Pattern(regexp = ValidationPatterns.BRANCH_CODE, message = "must be a 6-digit branch code") String branchCode,
        @NotBlank @Size(max = 60) String designation) {
}
