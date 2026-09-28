package com.bankms.security;

import com.bankms.config.AppProperties;
import com.bankms.entity.Role;
import com.bankms.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Issues and verifies short-lived HS256 access tokens. Refresh tokens are deliberately NOT JWTs:
 * they are opaque random values stored hashed server-side, so they can be revoked and rotated.
 */
@Service
public class JwtService {

    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_ROLE = "role";

    private final SecretKey key;
    private final String issuer;
    private final Duration accessTokenTtl;
    private final Clock clock;
    private final JwtParser parser;

    public JwtService(AppProperties properties, Clock clock) {
        AppProperties.Jwt jwt = properties.security().jwt();
        byte[] keyBytes = Decoders.BASE64.decode(jwt.secret());
        if (keyBytes.length < 32) {
            throw new IllegalStateException("app.security.jwt.secret must be a Base64 key of at least 256 bits");
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.issuer = jwt.issuer();
        this.accessTokenTtl = jwt.accessTokenTtl();
        this.clock = clock;
        this.parser = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .clock(() -> Date.from(Instant.now(clock)))
                .build();
    }

    public String issueAccessToken(User user) {
        Instant now = Instant.now(clock);
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(issuer)
                .subject(String.valueOf(user.getId()))
                .claim(CLAIM_EMAIL, user.getEmail())
                .claim(CLAIM_ROLE, user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenTtl)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * @throws io.jsonwebtoken.ExpiredJwtException when the token is expired
     * @throws JwtException                        for any other signature/format problem
     */
    public AuthUser parseAccessToken(String token) {
        Claims claims = parser.parseSignedClaims(token).getPayload();
        try {
            return new AuthUser(
                    Long.valueOf(claims.getSubject()),
                    claims.get(CLAIM_EMAIL, String.class),
                    Role.valueOf(claims.get(CLAIM_ROLE, String.class)));
        } catch (RuntimeException e) {
            throw new JwtException("Malformed claims", e);
        }
    }

    public long accessTokenTtlSeconds() {
        return accessTokenTtl.toSeconds();
    }
}
