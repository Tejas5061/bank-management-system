package com.bankms.dto.branch;

import com.bankms.util.ValidationPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** code is used on create only; the IFSC is derived from it and never changes. */
public record BranchRequest(
        @Pattern(regexp = ValidationPatterns.BRANCH_CODE, message = "must be a 6-digit branch code") String code,
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 255) String addressLine,
        @NotBlank @Size(max = 60) String city,
        @NotBlank @Size(max = 60) String state,
        @NotBlank @Pattern(regexp = ValidationPatterns.PINCODE, message = "must be a valid 6-digit PIN code") String pincode,
        @Pattern(regexp = "^[0-9]{10,15}$", message = "must be 10-15 digits") String phone,
        Boolean active) {
}
