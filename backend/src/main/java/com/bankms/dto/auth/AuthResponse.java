package com.bankms.dto.auth;

/**
 * Body of login/refresh. The refresh token is NOT here: it travels only in an HttpOnly cookie,
 * so JavaScript (and therefore an XSS payload) can never read it.
 */
public record AuthResponse(String accessToken, String tokenType, long expiresIn, UserSummary user) {

    public static AuthResponse bearer(String accessToken, long expiresIn, UserSummary user) {
        return new AuthResponse(accessToken, "Bearer", expiresIn, user);
    }
}
