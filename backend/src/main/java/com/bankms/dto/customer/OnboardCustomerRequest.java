package com.bankms.dto.customer;

import com.bankms.entity.AccountType;
import com.bankms.util.Adult;
import com.bankms.util.ValidationPatterns;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * In-branch onboarding by a teller. No password: a temporary one is generated and emailed.
 * branchCode defaults to the teller's own branch when omitted.
 */
public record OnboardCustomerRequest(
        @NotBlank @Size(min = 3, max = 100) String fullName,
        @NotBlank @Email @Size(max = 120) String email,
        @NotBlank @Pattern(regexp = ValidationPatterns.PHONE, message = "must be a valid 10-digit mobile number") String phone,
        @NotNull @Past @Adult LocalDate dateOfBirth,
        @NotBlank @Pattern(regexp = ValidationPatterns.PAN, message = "must be a valid PAN, e.g. ABCPE1234F") String panNumber,
        @NotBlank @Pattern(regexp = ValidationPatterns.AADHAAR, message = "must be a valid 12-digit Aadhaar number") String aadhaarNumber,
        @NotBlank @Size(max = 255) String addressLine,
        @NotBlank @Size(max = 60) String city,
        @NotBlank @Size(max = 60) String state,
        @NotBlank @Pattern(regexp = ValidationPatterns.PINCODE, message = "must be a valid 6-digit PIN code") String pincode,
        @Pattern(regexp = ValidationPatterns.BRANCH_CODE, message = "must be a 6-digit branch code") String branchCode,
        @NotNull AccountType accountType) {

    @AssertTrue(message = "accountType must be SAVINGS or CURRENT")
    public boolean isOperativeAccountType() {
        return accountType == null || accountType != AccountType.FIXED_DEPOSIT;
    }
}
