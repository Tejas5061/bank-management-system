package com.bankms.security;

import com.bankms.config.AppProperties;
import com.bankms.entity.Role;
import com.bankms.entity.User;
import com.bankms.support.TestProperties;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-28T06:00:00Z");
    private final AppProperties properties = TestProperties.create();

    @Test
    void roundTripsIdentityAndRole() {
        JwtService jwt = new JwtService(properties, Clock.fixed(NOW, ZoneOffset.UTC));
        AuthUser user = jwt.parseAccessToken(jwt.issueAccessToken(user()));
        assertThat(user).isEqualTo(new AuthUser(42L, "ananya@example.com", Role.CUSTOMER));
    }

    @Test
    void expiredTokenIsRejectedWithADistinctException() {
        String token = new JwtService(properties, Clock.fixed(NOW, ZoneOffset.UTC)).issueAccessToken(user());
        JwtService sixteenMinutesLater = new JwtService(properties, Clock.fixed(NOW.plus(Duration.ofMinutes(16)), ZoneOffset.UTC));
        assertThatThrownBy(() -> sixteenMinutesLater.parseAccessToken(token)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void tamperedTokenIsRejected() {
        JwtService jwt = new JwtService(properties, Clock.fixed(NOW, ZoneOffset.UTC));
        String token = jwt.issueAccessToken(user());
        String[] parts = token.split("\\.");
        // swap in a payload claiming ADMIN; the signature no longer matches
        String forgedPayload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"sub\":\"42\",\"email\":\"ananya@example.com\",\"role\":\"ADMIN\",\"iss\":\"bank-management-system\"}".getBytes());
        assertThatThrownBy(() -> jwt.parseAccessToken(parts[0] + "." + forgedPayload + "." + parts[2]))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void refusesAWeakSigningKey() {
        AppProperties weak = new AppProperties(properties.bank(),
                new AppProperties.Security(new AppProperties.Jwt("c2hvcnQ=", "x", Duration.ofMinutes(1), Duration.ofDays(1)),
                        properties.security().refreshCookie(), properties.security().lockout(),
                        properties.security().passwordReset(), 4),
                properties.rateLimit(), properties.cors(), properties.transfer(), properties.mail(), properties.bootstrap());
        assertThatThrownBy(() -> new JwtService(weak, Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("256 bits");
    }

    private static User user() {
        User user = new User("ananya@example.com", "hash", "Ananya Sharma", "9820012345", Role.CUSTOMER) {
            @Override
            public Long getId() {
                return 42L;
            }
        };
        return user;
    }
}
