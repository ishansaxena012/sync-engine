package com.ishan.syncCanvas.security.jwt;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Tracks revoked refresh tokens in Redis so refresh is no longer purely stateless.
 *
 * <p>Without this, a refresh token was valid for its full lifetime (7 days) even after
 * the user "logged out", and a leaked refresh token could be replayed indefinitely.
 * Every refresh now rotates the token (the just-used one is revoked immediately), and
 * logout revokes whatever refresh token the client hands it — closing both gaps.
 *
 * <p>Revocation entries are keyed by the token's {@code jti} claim and expire from
 * Redis on their own once the token itself would have expired anyway, so this never
 * grows unbounded.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenRevocationService {

    private static final String KEY_PREFIX = "syncCanvas:revokedRefreshToken:";

    private final StringRedisTemplate redisTemplate;

    public void revoke(String jti, java.util.Date expiresAt) {
        if (jti == null) {
            return;
        }
        Duration ttl = Duration.between(Instant.now(), expiresAt.toInstant());
        if (ttl.isNegative() || ttl.isZero()) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + jti, "1", ttl);
        } catch (Exception ex) {
            log.error("Failed to revoke refresh token {}", jti, ex);
        }
    }

    public boolean isRevoked(String jti) {
        if (jti == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + jti));
        } catch (Exception ex) {
            // Fail closed on refresh (unlike the operation-idempotency check): if we
            // can't confirm a refresh token wasn't revoked, prefer forcing a re-login
            // over letting a revoked token slip through during a Redis outage.
            log.error("Revocation check failed for refresh token {}; treating as revoked (fail-closed)", jti, ex);
            return true;
        }
    }
}
