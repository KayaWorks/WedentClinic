package com.wedent.clinic.security.ratelimit;

import com.wedent.clinic.config.CacheProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis-backed sliding-window login attempt limiter.  Keys are the composite
 * {@code client-ip|email} so a single client hammering one account gets
 * blocked but a shared NAT doesn't collectively starve unrelated users.
 *
 * <p>Uses an atomic {@code INCR} + conditional {@code EXPIRE} so horizontally-
 * scaled nodes share the same counter — the previous in-memory implementation
 * let an attacker round-robin across nodes and avoid the limit entirely.</p>
 *
 * <p>Key layout: {@code {prefix}rl:login:{ip|email}} → integer counter.
 * TTL is set on first miss and not refreshed; the window is a true sliding
 * window starting at the first failure and not extended by later ones — this
 * matches the in-memory semantics callers already rely on.</p>
 */
@Component
@RequiredArgsConstructor
public class LoginRateLimiter {

    /** Max failed attempts within the window before the key is blocked. */
    static final int MAX_ATTEMPTS = 10;

    /** Window during which failed attempts are counted. */
    static final Duration WINDOW = Duration.ofMinutes(10);

    private static final String KEY = "rl:login:";

    private final StringRedisTemplate redis;
    private final CacheProperties cacheProperties;

    public boolean isBlocked(String key) {
        String raw = redis.opsForValue().get(redisKey(key));
        if (raw == null) return false;
        return parseInt(raw) >= MAX_ATTEMPTS;
    }

    public void onFailure(String key) {
        String k = redisKey(key);
        Long count = redis.opsForValue().increment(k);
        // Only set TTL on the first failure of the window.  INCR returns 1 in that case;
        // on later increments we leave the original expiry alone so the window slides.
        if (count != null && count == 1L) {
            redis.expire(k, WINDOW);
        }
    }

    public void onSuccess(String key) {
        redis.delete(redisKey(key));
    }

    public static String keyOf(String clientIp, String email) {
        String e = email == null ? "" : email.trim().toLowerCase();
        String ip = clientIp == null ? "" : clientIp;
        return ip + "|" + e;
    }

    private String redisKey(String rawKey) {
        return cacheProperties.keyPrefix() + KEY + rawKey;
    }

    private static int parseInt(String s) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; }
    }
}
