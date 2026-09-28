package com.bankms.service.auth;

import com.bankms.audit.AuditAction;
import com.bankms.audit.AuditEntry;
import com.bankms.audit.AuditOutcome;
import com.bankms.audit.Audited;
import com.bankms.config.AppProperties;
import com.bankms.dto.auth.AuthResponse;
import com.bankms.dto.auth.ChangePasswordRequest;
import com.bankms.dto.auth.ForgotPasswordRequest;
import com.bankms.dto.auth.LoginRequest;
import com.bankms.dto.auth.ResetPasswordRequest;
import com.bankms.dto.auth.UserSummary;
import com.bankms.entity.NotificationType;
import com.bankms.entity.PasswordResetOtp;
import com.bankms.entity.RefreshToken;
import com.bankms.entity.User;
import com.bankms.exception.BankException;
import com.bankms.exception.ErrorCode;
import com.bankms.repository.PasswordResetOtpRepository;
import com.bankms.repository.UserRepository;
import com.bankms.security.JwtService;
import com.bankms.service.AuditService;
import com.bankms.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    /** A refresh token reused within this window is treated as two tabs racing, not as theft. */
    private static final Duration ROTATION_GRACE = Duration.ofSeconds(10);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH);

    private final UserRepository users;
    private final PasswordResetOtpRepository otps;
    private final RefreshTokenService refreshTokens;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notifications;
    private final AuditService auditService;
    private final AppProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    /** Hash of a random string: comparing against it keeps "unknown email" as slow as "wrong password". */
    private String dummyHash;

    /**
     * noRollbackFor: the failed-attempt counter and lockout are written right before we throw
     * INVALID_CREDENTIALS. With the default rollback-on-RuntimeException they would be undone,
     * and the lockout would never trigger.
     */
    @Audited(action = AuditAction.LOGIN, entityType = "USER", actor = "#request.email")
    @Transactional(noRollbackFor = BankException.class)
    public LoginResult login(LoginRequest request, String clientIp) {
        String email = normalizeEmail(request.email());
        Instant now = Instant.now(clock);
        User user = users.findByEmailForUpdate(email).orElse(null);
        if (user == null) {
            passwordEncoder.matches(request.password(), dummyHash());
            throw new BankException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (user.isLocked(now)) {
            throw new BankException(ErrorCode.ACCOUNT_LOCKED, lockedMessage(user));
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            AppProperties.Lockout lockout = properties.security().lockout();
            boolean lockedNow = user.registerFailedLogin(lockout.maxFailedAttempts(), now.plus(lockout.duration()));
            if (lockedNow) {
                notifications.notifyAndEmail(user, NotificationType.SECURITY, "Account temporarily locked",
                        "We locked your account after %d failed sign-in attempts. It unlocks automatically at %s, or you can reset your password now. If this wasn't you, contact your branch."
                                .formatted(lockout.maxFailedAttempts(), TIME.format(user.getLockedUntil().atZone(zone()))));
                throw new BankException(ErrorCode.ACCOUNT_LOCKED, lockedMessage(user));
            }
            throw new BankException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (!user.isEnabled()) {
            throw new BankException(ErrorCode.ACCOUNT_DISABLED);
        }
        user.registerSuccessfulLogin(now);
        return issueTokens(user, null, clientIp);
    }

    /**
     * Rotation: each refresh token works once. Presenting an already-rotated token means it was
     * copied, so the whole token family is revoked and every session from that login ends.
     */
    @Transactional(noRollbackFor = BankException.class)
    public LoginResult refresh(String rawToken, String clientIp) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new BankException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        Instant now = Instant.now(clock);
        RefreshToken token = refreshTokens.findForRotation(rawToken)
                .orElseThrow(() -> new BankException(ErrorCode.INVALID_REFRESH_TOKEN));
        User user = token.getUser();

        if (token.isRevoked()) {
            if (token.getRevokedAt().plus(ROTATION_GRACE).isBefore(now)) {
                refreshTokens.revokeFamily(token.getFamilyId());
                log.warn("Refresh token reuse detected for user {}; family {} revoked", user.getId(), token.getFamilyId());
                auditService.record(AuditEntry.builder()
                        .actorId(user.getId()).actorEmail(user.getEmail()).actorRole(user.getRole().name())
                        .action(AuditAction.TOKEN_REUSE_DETECTED).outcome(AuditOutcome.FAILURE)
                        .entityType("USER").entityId(String.valueOf(user.getId()))
                        .details("Rotated refresh token presented again; all sessions from that login revoked")
                        .ipAddress(clientIp).build());
                notifications.notifyAndEmail(user, NotificationType.SECURITY, "Suspicious sign-in activity",
                        "An old session token for your account was used again, so we signed that session out everywhere. Please sign in again, and change your password if you did not expect this.");
            }
            throw new BankException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        if (token.isExpired(now) || !user.isEnabled() || user.isLocked(now)) {
            token.setRevokedAt(now);
            throw new BankException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        token.setRevokedAt(now);
        return issueTokens(user, token.getFamilyId(), clientIp);
    }

    @Audited(action = AuditAction.LOGOUT, entityType = "USER")
    @Transactional
    public void logout(String rawToken) {
        if (rawToken != null && !rawToken.isBlank()) {
            refreshTokens.find(rawToken).ifPresent(token -> refreshTokens.revokeFamily(token.getFamilyId()));
        }
    }

    /**
     * Always behaves the same whether or not the email exists, so this endpoint cannot be used to
     * discover which emails are registered.
     */
    @Audited(action = AuditAction.PASSWORD_RESET_REQUESTED, entityType = "USER", actor = "#request.email")
    @Transactional
    public void requestPasswordReset(ForgotPasswordRequest request) {
        String otp = "%06d".formatted(random.nextInt(1_000_000));
        String otpHash = passwordEncoder.encode(otp); // hashed even for unknown emails: equal timing
        users.findByEmail(normalizeEmail(request.email()))
                .filter(User::isEnabled)
                .ifPresent(user -> {
                    Instant now = Instant.now(clock);
                    otps.invalidateActive(user.getId(), now);
                    PasswordResetOtp entity = new PasswordResetOtp();
                    entity.setUser(user);
                    entity.setOtpHash(otpHash);
                    entity.setExpiresAt(now.plus(properties.security().passwordReset().otpTtl()));
                    otps.save(entity);
                    notifications.email(user.getEmail(), "Your password reset code",
                            "Dear %s,\n\nYour one-time password reset code is %s. It expires in %d minutes.\n\nIf you did not ask to reset your password, you can ignore this email."
                                    .formatted(user.getFullName(), otp, properties.security().passwordReset().otpTtl().toMinutes()));
                });
    }

    @Audited(action = AuditAction.PASSWORD_RESET, entityType = "USER", actor = "#request.email")
    @Transactional(noRollbackFor = BankException.class)
    public void resetPassword(ResetPasswordRequest request) {
        Instant now = Instant.now(clock);
        User user = users.findByEmail(normalizeEmail(request.email()))
                .orElseThrow(() -> new BankException(ErrorCode.INVALID_OTP));
        PasswordResetOtp otp = otps.findFirstByUserIdAndConsumedAtIsNullOrderByCreatedAtDesc(user.getId())
                .orElseThrow(() -> new BankException(ErrorCode.INVALID_OTP));
        int maxAttempts = properties.security().passwordReset().maxAttempts();
        if (!otp.isUsable(now, maxAttempts)) {
            throw new BankException(ErrorCode.INVALID_OTP, "The code has expired or was tried too many times; request a new one");
        }
        if (!passwordEncoder.matches(request.otp(), otp.getOtpHash())) {
            otp.setAttempts(otp.getAttempts() + 1);
            throw new BankException(ErrorCode.INVALID_OTP);
        }
        otp.setConsumedAt(now);
        user.changePassword(passwordEncoder.encode(request.newPassword()), now);
        refreshTokens.revokeAll(user.getId());
        notifications.notifyAndEmail(user, NotificationType.SECURITY, "Password changed",
                "Your password was reset and all your sessions were signed out. If this wasn't you, contact your branch immediately.");
    }

    @Audited(action = AuditAction.PASSWORD_CHANGED, entityType = "USER", entityId = "#userId")
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = users.findById(userId).orElseThrow(() -> BankException.notFound("User", userId));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BankException(ErrorCode.VALIDATION_FAILED, "Current password is incorrect");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BankException(ErrorCode.VALIDATION_FAILED, "New password must be different from the current one");
        }
        user.changePassword(passwordEncoder.encode(request.newPassword()), Instant.now(clock));
        refreshTokens.revokeAll(user.getId());
        notifications.notifyAndEmail(user, NotificationType.SECURITY, "Password changed",
                "Your password was changed and your other sessions were signed out.");
    }

    @Transactional(readOnly = true)
    public UserSummary me(Long userId) {
        return users.findById(userId).map(UserSummary::of).orElseThrow(() -> BankException.notFound("User", userId));
    }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private LoginResult issueTokens(User user, String familyId, String clientIp) {
        String refreshToken = refreshTokens.issue(user, familyId, clientIp);
        AuthResponse body = AuthResponse.bearer(jwtService.issueAccessToken(user), jwtService.accessTokenTtlSeconds(), UserSummary.of(user));
        return new LoginResult(body, refreshToken);
    }

    private String lockedMessage(User user) {
        return "Too many failed attempts. Your account is locked until %s; reset your password to unlock it now."
                .formatted(TIME.format(user.getLockedUntil().atZone(zone())));
    }

    private ZoneId zone() {
        return properties.bank().timezone();
    }

    private String dummyHash() {
        if (dummyHash == null) {
            dummyHash = passwordEncoder.encode("dummy-password-for-timing-" + random.nextLong());
        }
        return dummyHash;
    }
}
