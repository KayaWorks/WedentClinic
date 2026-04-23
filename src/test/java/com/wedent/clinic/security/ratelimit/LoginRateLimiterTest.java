package com.wedent.clinic.security.ratelimit;

import com.wedent.clinic.config.CacheProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Exercises the Redis-backed limiter without a real Redis: a {@link StringRedisTemplate}
 * mock stands in for the cluster and we verify both the observable behaviour
 * (blocks after the threshold, clears on success) and the wire-level semantics
 * (TTL is only set once, keys are namespaced).
 */
class LoginRateLimiterTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> ops;
    private LoginRateLimiter limiter;
    private final Map<String, Long> counters = new HashMap<>();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = Mockito.mock(StringRedisTemplate.class);
        ops = Mockito.mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);

        // Minimal in-memory stand-in for INCR — each call advances the stored
        // counter, and GET reads whatever is stored.  Enough to exercise the
        // threshold logic without a real redis.
        when(ops.increment(anyString())).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            long next = counters.getOrDefault(key, 0L) + 1;
            counters.put(key, next);
            return next;
        });
        when(ops.get(anyString())).thenAnswer(inv -> {
            Long v = counters.get((String) inv.getArgument(0));
            return v == null ? null : v.toString();
        });
        when(redis.delete(anyString())).thenAnswer(inv -> {
            counters.remove((String) inv.getArgument(0));
            return true;
        });

        CacheProperties props = new CacheProperties(Duration.ofMinutes(10), "wedent:");
        limiter = new LoginRateLimiter(redis, props);
    }

    @Test
    void blocksAfterMaxAttempts() {
        String key = LoginRateLimiter.keyOf("10.0.0.1", "user@example.com");

        for (int i = 0; i < LoginRateLimiter.MAX_ATTEMPTS; i++) {
            assertThat(limiter.isBlocked(key)).as("attempt %d", i).isFalse();
            limiter.onFailure(key);
        }
        assertThat(limiter.isBlocked(key)).isTrue();
    }

    @Test
    void ttlIsSetOnlyOnTheFirstFailureOfTheWindow() {
        String key = LoginRateLimiter.keyOf("10.0.0.1", "user@example.com");

        limiter.onFailure(key);
        limiter.onFailure(key);
        limiter.onFailure(key);

        // EXPIRE is called exactly once (on the 1 → 1 transition). This preserves
        // the sliding-window semantics: later failures don't keep pushing out the cutoff.
        verify(redis, times(1)).expire(anyString(), eq(LoginRateLimiter.WINDOW));
    }

    @Test
    void successfulLogin_clearsTheKey() {
        String key = LoginRateLimiter.keyOf("10.0.0.1", "user@example.com");

        limiter.onFailure(key);
        limiter.onSuccess(key);

        verify(redis).delete(ArgumentCaptor.forClass(String.class).capture());
        assertThat(counters).isEmpty();
    }

    @Test
    void keyOf_normalizesEmailCaseAndTrimsWhitespace() {
        assertThat(LoginRateLimiter.keyOf("1.1.1.1", "  Foo@Bar.com "))
                .isEqualTo(LoginRateLimiter.keyOf("1.1.1.1", "foo@bar.com"));
    }

    @Test
    void keysUseTheConfiguredPrefix() {
        String key = LoginRateLimiter.keyOf("10.0.0.1", "user@example.com");
        limiter.onFailure(key);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(ops).increment(keyCaptor.capture());
        // Namespaced with the configured prefix so the same cluster can be shared.
        assertThat(keyCaptor.getValue()).startsWith("wedent:rl:login:");
    }

    @Test
    void unbloggedKeyNeverHitsExpire() {
        String key = LoginRateLimiter.keyOf("10.0.0.1", "user@example.com");
        limiter.isBlocked(key);
        verify(redis, never()).expire(any(), any());
    }
}
