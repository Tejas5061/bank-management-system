package com.bankms.dto.customer;

import com.bankms.util.ValidationPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Contact details only; identity fields (name, DOB, PAN, Aadhaar) change through KYC, not self-service. */
public record UpdateProfileRequest(
        @NotBlank @Pattern(regexp = ValidationPatterns.PHONE, message = "must be a valid 10-digit mobile number") String phone,
        @NotBlank @Size(max = 255) String addressLine,
        @NotBlank @Size(max = 60) String city,
        @NotBlank @Size(max = 60) String state,
        @NotBlank @Pattern(regexp = ValidationPatterns.PINCODE, message = "must be a valid 6-digit PIN code") String pincode) {
}
