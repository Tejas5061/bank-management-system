package com.bankms.controller;

import com.bankms.dto.auth.AuthResponse;
import com.bankms.dto.auth.ChangePasswordRequest;
import com.bankms.dto.auth.ForgotPasswordRequest;
import com.bankms.dto.auth.LoginRequest;
import com.bankms.dto.auth.RegisterRequest;
import com.bankms.dto.auth.RegistrationResponse;
import com.bankms.dto.auth.ResetPasswordRequest;
import com.bankms.dto.auth.UserSummary;
import com.bankms.dto.common.MessageResponse;
import com.bankms.security.AuthUser;
import com.bankms.security.RefreshTokenCookies;
import com.bankms.service.CustomerService;
import com.bankms.service.auth.AuthService;
import com.bankms.service.auth.LoginResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Authentication")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CustomerService customerService;
    private final RefreshTokenCookies cookies;

    @Operation(summary = "Register as a customer (KYC starts as PENDING)")
    @SecurityRequirements
    @PostMapping("/register")
    public ResponseEntity<RegistrationResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(customerService.register(request));
    }

    @Operation(summary = "Sign in; the refresh token is set as an HttpOnly cookie")
    @SecurityRequirements
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return withRefreshCookie(authService.login(request, http.getRemoteAddr()));
    }

    @Operation(summary = "Exchange the refresh-token cookie for a new access token (rotates the cookie)")
    @SecurityRequirements
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(HttpServletRequest http) {
        return withRefreshCookie(authService.refresh(cookies.read(http), http.getRemoteAddr()));
    }

    @Operation(summary = "Sign out: revokes the refresh token and clears the cookie")
    @SecurityRequirements
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest http) {
        authService.logout(cookies.read(http));
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookies.clear().toString()).build();
    }

    @Operation(summary = "Email a 6-digit password reset code (always returns 202)")
    @SecurityRequirements
    @PostMapping("/password/forgot")
    public ResponseEntity<MessageResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.requestPasswordReset(request);
        return ResponseEntity.accepted()
                .body(new MessageResponse("If an account exists for this email, a reset code has been sent."));
    }

    @Operation(summary = "Reset the password with the emailed code")
    @SecurityRequirements
    @PostMapping("/password/reset")
    public MessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return new MessageResponse("Password updated. Please sign in with your new password.");
    }

    @Operation(summary = "Change password (signs out all sessions)")
    @PostMapping("/password/change")
    public ResponseEntity<MessageResponse> changePassword(@AuthenticationPrincipal AuthUser user,
                                                         @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(user.id(), request);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.clear().toString())
                .body(new MessageResponse("Password changed. Please sign in again."));
    }

    @Operation(summary = "The signed-in user")
    @GetMapping("/me")
    public UserSummary me(@AuthenticationPrincipal AuthUser user) {
        return authService.me(user.id());
    }

    private ResponseEntity<AuthResponse> withRefreshCookie(LoginResult result) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.issue(result.refreshToken()).toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(result.response());
    }
}
