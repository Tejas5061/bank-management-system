package com.bankms.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.time.ZoneId;
import java.util.List;

/**
 * All application settings in one typed, validated tree. A missing or malformed value (for example
 * no JWT_SECRET) stops the application at startup instead of failing on the first request.
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @Valid @NotNull Bank bank,
        @Valid @NotNull Security security,
        @Valid @NotNull RateLimit rateLimit,
        @Valid @NotNull Cors cors,
        @Valid @NotNull Transfer transfer,
        @Valid @NotNull Mail mail,
        @Valid @NotNull Bootstrap bootstrap) {

    public record Bank(
            @NotBlank String name,
            @NotBlank @Pattern(regexp = "[A-Z]{4}") String ifscPrefix,
            @NotNull ZoneId timezone,
            @NotBlank String currency) {
    }

    public record Security(
            @Valid @NotNull Jwt jwt,
            @Valid @NotNull RefreshCookie refreshCookie,
            @Valid @NotNull Lockout lockout,
            @Valid @NotNull PasswordReset passwordReset,
            @Min(4) @Max(31) int bcryptStrength) {
    }

    /** secret is a Base64-encoded HMAC key of at least 256 bits (checked again in JwtService). */
    public record Jwt(
            @NotBlank String secret,
            @NotBlank String issuer,
            @NotNull Duration accessTokenTtl,
            @NotNull Duration refreshTokenTtl) {
    }

    public record RefreshCookie(
            @NotBlank String name,
            @NotBlank String path,
            boolean secure,
            @NotBlank @Pattern(regexp = "Strict|Lax|None") String sameSite) {
    }

    public record Lockout(@Min(1) int maxFailedAttempts, @NotNull Duration duration) {
    }

    public record PasswordReset(@NotNull Duration otpTtl, @Min(1) int maxAttempts) {
    }

    public record RateLimit(@Min(1) int authCapacity, @NotNull Duration authRefillPeriod) {
    }

    public record Cors(@NotEmpty List<String> allowedOrigins) {
    }

    public record Transfer(@NotNull Duration beneficiaryCoolingPeriod, @NotNull Duration idempotencyTtl) {
    }

    public record Mail(boolean enabled, @NotBlank String from) {
    }

    /** Optional first-admin bootstrap for environments without demo seed data. */
    public record Bootstrap(String adminEmail, String adminPassword, String adminName) {
    }
}
