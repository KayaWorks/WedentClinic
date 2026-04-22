package com.wedent.clinic.security.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class LoginRateLimiterTest {

    @Test
    void blocksAfterMaxAttempts_thenUnblocksAfterWindow() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        LoginRateLimiter limiter = new LoginRateLimiter(clock);

        String key = LoginRateLimiter.keyOf("10.0.0.1", "user@example.com");

        for (int i = 0; i < LoginRateLimiter.MAX_ATTEMPTS; i++) {
            assertThat(limiter.isBlocked(key)).as("not blocked before threshold, attempt %d", i).isFalse();
            limiter.onFailure(key);
        }
        assertThat(limiter.isBlocked(key)).as("blocked after threshold").isTrue();

        // Advance past the window; bucket should be considered expired and unblocked.
        clock.advance(LoginRateLimiter.WINDOW.plusSeconds(1));
        assertThat(limiter.isBlocked(key)).as("unblocked after window").isFalse();
    }

    @Test
    void successfulLogin_resetsCounter() {
        LoginRateLimiter limiter = new LoginRateLimiter();
        String key = LoginRateLimiter.keyOf("10.0.0.1", "user@example.com");

        for (int i = 0; i < 3; i++) limiter.onFailure(key);
        limiter.onSuccess(key);

        // Counter was reset, so MAX-1 further failures should still be under the threshold.
        for (int i = 0; i < LoginRateLimiter.MAX_ATTEMPTS - 1; i++) {
            assertThat(limiter.isBlocked(key)).isFalse();
            limiter.onFailure(key);
        }
        assertThat(limiter.isBlocked(key)).isFalse();
    }

    @Test
    void keyOf_normalizesEmailCaseAndTrimsWhitespace() {
        assertThat(LoginRateLimiter.keyOf("1.1.1.1", "  Foo@Bar.com "))
                .isEqualTo(LoginRateLimiter.keyOf("1.1.1.1", "foo@bar.com"));
    }

    /** Minimal mutable Clock for time-controlled tests without external libraries. */
    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) { this.now = start; }

        void advance(java.time.Duration d) { this.now = this.now.plus(d); }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
