package com.bankms.service.auth;

import com.bankms.config.AppProperties;
import com.bankms.entity.RefreshToken;
import com.bankms.entity.User;
import com.bankms.repository.RefreshTokenRepository;
import com.bankms.util.Hashing;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository tokens;
    private final AppProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    /** Returns the raw token for the cookie; only its SHA-256 is stored. */
    @Transactional(propagation = Propagation.MANDATORY)
    public String issue(User user, String familyId, String clientIp) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(Hashing.sha256Hex(raw));
        token.setFamilyId(familyId != null ? familyId : UUID.randomUUID().toString());
        token.setExpiresAt(Instant.now(clock).plus(properties.security().jwt().refreshTokenTtl()));
        token.setCreatedIp(clientIp);
        tokens.save(token);
        return raw;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<RefreshToken> findForRotation(String rawToken) {
        return tokens.findByTokenHashForUpdate(Hashing.sha256Hex(rawToken));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<RefreshToken> find(String rawToken) {
        return tokens.findByTokenHash(Hashing.sha256Hex(rawToken));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void revokeFamily(String familyId) {
        tokens.revokeFamily(familyId, Instant.now(clock));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void revokeAll(Long userId) {
        tokens.revokeAllForUser(userId, Instant.now(clock));
    }

    @Transactional
    public int purgeExpired() {
        return tokens.deleteExpiredBefore(Instant.now(clock));
    }
}
