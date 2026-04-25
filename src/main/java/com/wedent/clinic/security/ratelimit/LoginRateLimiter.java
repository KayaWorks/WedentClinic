package com.wedent.clinic.security.ratelimit;

import com.wedent.clinic.config.CacheProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class LoginRateLimiter {

    public static final int MAX_ATTEMPTS = 10;
    public static final Duration WINDOW = Duration.ofMinutes(10);
    private static final String KEY_PREFIX = "rl:login:";

    private final StringRedisTemplate redis;
    private final String keyPrefix;

    public LoginRateLimiter(StringRedisTemplate redis, CacheProperties cacheProperties) {
        this.redis = redis;
        this.keyPrefix = cacheProperties.keyPrefix();
    }

    public static String keyOf(String ip, String email) {
        String normalized = email == null ? "" : email.strip().toLowerCase();
        return ip + ":" + normalized;
    }

    public boolean isBlocked(String key) {
        String raw = redis.opsForValue().get(redisKey(key));
        if (raw == null) return false;
        try {
            return Long.parseLong(raw) >= MAX_ATTEMPTS;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public void onFailure(String key) {
        Long count = redis.opsForValue().increment(redisKey(key));
        if (count != null && count == 1L) {
            redis.expire(redisKey(key), WINDOW);
        }
    }

    public void onSuccess(String key) {
        redis.delete(redisKey(key));
    }

    private String redisKey(String key) {
        return keyPrefix + KEY_PREFIX + key;
    }
}
