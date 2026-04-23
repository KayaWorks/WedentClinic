package com.wedent.clinic.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Tunables for the Redis-backed cache, JWT blacklist, refresh-token store,
 * and distributed rate limiter.  Kept in one place so ops can point every
 * Redis-hitting feature at the same cluster via a single env-var set.
 *
 * <p>Redis itself is a hard dependency (refresh tokens live there) — there
 * is no "disabled" mode.  Connection settings come from the stock
 * {@code spring.data.redis.*} keys.</p>
 */
@ConfigurationProperties(prefix = "app.redis")
public record CacheProperties(
        /** Default TTL for {@code @Cacheable} entries that don't set their own. */
        Duration defaultTtl,
        /** Prefix applied to every key this service writes. Makes a shared cluster safe. */
        String keyPrefix
) {
    public CacheProperties {
        if (defaultTtl == null) defaultTtl = Duration.ofMinutes(10);
        if (keyPrefix == null || keyPrefix.isBlank()) keyPrefix = "wedent:";
    }
}
