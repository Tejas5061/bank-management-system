package com.bankms.security;

import com.bankms.config.AppProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;

/**
 * The refresh token lives only in this cookie: HttpOnly (no JavaScript access), SameSite=Strict
 * (never sent on cross-site requests), Secure in production, and Path=/api/v1/auth so it is not
 * even sent to the rest of the API.
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenCookies {

    private final AppProperties properties;

    public ResponseCookie issue(String token) {
        return build(token, properties.security().jwt().refreshTokenTtl());
    }

    public ResponseCookie clear() {
        return build("", Duration.ZERO);
    }

    public String read(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        String name = properties.security().refreshCookie().name();
        return Arrays.stream(request.getCookies())
                .filter(c -> name.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    private ResponseCookie build(String value, Duration maxAge) {
        AppProperties.RefreshCookie config = properties.security().refreshCookie();
        return ResponseCookie.from(config.name(), value)
                .httpOnly(true)
                .secure(config.secure())
                .sameSite(config.sameSite())
                .path(config.path())
                .maxAge(maxAge)
                .build();
    }
}
