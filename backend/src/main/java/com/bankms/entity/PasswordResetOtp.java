package com.bankms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "password_reset_otps")
@Getter
@Setter
@NoArgsConstructor
public class PasswordResetOtp extends CreatedAtEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    /** BCrypt hash; the OTP itself is only ever in the email. */
    @Column(name = "otp_hash", nullable = false, length = 100, updatable = false)
    private String otpHash;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    public boolean isUsable(Instant now, int maxAttempts) {
        return consumedAt == null && expiresAt.isAfter(now) && attempts < maxAttempts;
    }
}
