package com.bankms.dto.auth;

import com.bankms.util.ValidationPatterns;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank @Email @Size(max = 120) String email,
        @NotBlank @Pattern(regexp = "^[0-9]{6}$", message = "must be the 6-digit code from the email") String otp,
        @NotBlank @Pattern(regexp = ValidationPatterns.STRONG_PASSWORD, message = ValidationPatterns.STRONG_PASSWORD_MESSAGE) String newPassword) {

    @Override
    public String toString() {
        return "ResetPasswordRequest[email=" + email + "]";
    }
}
