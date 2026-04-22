package com.wedent.clinic.security.ratelimit;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-key sliding-window login attempt limiter. Keys are composite
 * {@code client-ip|email} strings so a single client hammering one account
 * gets blocked, but a shared NAT doesn't collectively starve unrelated users.
 *
 * <p>In-memory only — acceptable for a single-node deployment but MUST be
 * replaced with Redis (or similar) if the app scales horizontally, otherwise
 * each node enforces the limit independently and an attacker can simply round-
 * robin across them.
 *
 * <p>Failure count is reset on a successful login via {@link #onSuccess(String)}.
 */
@Component
public class LoginRateLimiter {

    /** Max failed attempts within the window before the key is blocked. */
    static final int MAX_ATTEMPTS = 10;

    /** Window during which failed attempts are counted. */
    static final Duration WINDOW = Duration.ofMinutes(10);

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final Clock clock;

    public LoginRateLimiter() {
        this(Clock.systemUTC());
    }

    LoginRateLimiter(Clock clock) {
        this.clock = clock;
    }

    /**
     * @return true if the key is currently over the threshold; the caller must
     *         reject the request immediately without invoking the downstream
     *         login service.
     */
    public boolean isBlocked(String key) {
        Bucket bucket = buckets.get(key);
        if (bucket == null) {
            return false;
        }
        Instant now = clock.instant();
        if (bucket.expired(now)) {
            buckets.remove(key, bucket);
            return false;
        }
        return bucket.count.get() >= MAX_ATTEMPTS;
    }

    /** Records a failed login attempt. */
    public void onFailure(String key) {
        Instant now = clock.instant();
        buckets.compute(key, (ignored, existing) -> {
            if (existing == null || existing.expired(now)) {
                Bucket fresh = new Bucket(now.plus(WINDOW));
                fresh.count.set(1);
                return fresh;
            }
            existing.count.incrementAndGet();
            return existing;
        });
    }

    /** Clears the failure counter after a successful login. */
    public void onSuccess(String key) {
        buckets.remove(key);
    }

    public static String keyOf(String clientIp, String email) {
        String e = email == null ? "" : email.trim().toLowerCase();
        String ip = clientIp == null ? "" : clientIp;
        return ip + "|" + e;
    }

    private static final class Bucket {
        private final Instant expiresAt;
        private final AtomicInteger count = new AtomicInteger();

        Bucket(Instant expiresAt) {
            this.expiresAt = expiresAt;
        }

        boolean expired(Instant now) {
            return !now.isBefore(expiresAt);
        }
    }
}
