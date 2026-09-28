package com.bankms.dto.auth;

import com.bankms.util.ValidationPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank @Size(max = 64) String currentPassword,
        @NotBlank @Pattern(regexp = ValidationPatterns.STRONG_PASSWORD, message = ValidationPatterns.STRONG_PASSWORD_MESSAGE) String newPassword) {

    @Override
    public String toString() {
        return "ChangePasswordRequest[***]";
    }
}
