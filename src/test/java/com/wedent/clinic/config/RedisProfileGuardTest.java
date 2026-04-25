package com.wedent.clinic.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedisProfileGuardTest {

    @Test
    void railwayProfile_requiresRedisUrl() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("railway");

        assertThatThrownBy(() -> RedisProfileGuard.validate(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("REDIS_URL is required");
    }

    @Test
    void prodProfile_rejectsPlaceholderRedisUrl() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("REDIS_URL", "${REDIS_URL}");
        environment.setActiveProfiles("prod");

        assertThatThrownBy(() -> RedisProfileGuard.validate(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not a placeholder");
    }

    @Test
    void devProfile_rejectsRailwayPrivateRedisHost() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.data.redis.url", "redis://default:secret@redis.railway.internal:6379");
        environment.setActiveProfiles("dev");

        assertThatThrownBy(() -> RedisProfileGuard.validate(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("redis.railway.internal works only inside Railway");
    }

    @Test
    void devProfile_allowsLocalRedisUrl() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.data.redis.url", "redis://localhost:6379");
        environment.setActiveProfiles("dev");

        assertThatCode(() -> RedisProfileGuard.validate(environment))
                .doesNotThrowAnyException();
    }
}
