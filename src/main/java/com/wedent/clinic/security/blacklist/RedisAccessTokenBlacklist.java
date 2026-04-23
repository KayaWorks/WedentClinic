package com.wedent.clinic.security.blacklist;

import com.wedent.clinic.config.CacheProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Redis-backed implementation — one key per blacklisted {@code jti}.
 *
 * <p>Key: {@code {prefix}jwt:bl:{jti}} → {@code "1"}<br>
 * TTL: exactly the remaining lifetime of the original token, so:
 * <ul>
 *   <li>Storage tracks the concurrent-logout churn, not cumulative history.</li>
 *   <li>After the token would have expired anyway, the entry evicts itself
 *       and the {@link JwtAuthenticationFilter} lookup returns {@code false}
 *       again — no manual cleanup job needed.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class RedisAccessTokenBlacklist implements AccessTokenBlacklist {

    private static final String KEY = "jwt:bl:";

    private final StringRedisTemplate redis;
    private final CacheProperties cacheProperties;

    @Override
    public void blacklist(String jti, Instant expiresAt) {
        if (jti == null || jti.isBlank() || expiresAt == null) return;
        Duration ttl = Duration.between(Instant.now(), expiresAt);
        if (ttl.isZero() || ttl.isNegative()) return; // already expired → nothing to block
        redis.opsForValue().set(key(jti), "1", ttl);
    }

    @Override
    public boolean isBlacklisted(String jti) {
        if (jti == null || jti.isBlank()) return false;
        Boolean exists = redis.hasKey(key(jti));
        return Boolean.TRUE.equals(exists);
    }

    private String key(String jti) {
        return cacheProperties.keyPrefix() + KEY + jti;
    }
}
