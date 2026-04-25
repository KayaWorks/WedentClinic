package com.wedent.clinic.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class RedisUrlEnvironmentPostProcessorTest {

    private final RedisUrlEnvironmentPostProcessor processor = new RedisUrlEnvironmentPostProcessor();

    @Test
    void prefersRedisUrl() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("REDIS_URL", "redis://direct.example.internal:6379")
                .withProperty("REDIS_PUBLIC_URL", "redis://public.example.net:12345");

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("spring.data.redis.url"))
                .isEqualTo("redis://direct.example.internal:6379");
    }

    @Test
    void fallsBackToRedisPublicUrl() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("REDIS_PUBLIC_URL", "redis://public.example.net:12345");

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("spring.data.redis.url"))
                .isEqualTo("redis://public.example.net:12345");
    }

    @Test
    void buildsUrlFromRailwaySplitVariables() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("REDISHOST", "redis.example.internal")
                .withProperty("REDISPORT", "6379")
                .withProperty("REDISUSER", "appuser")
                .withProperty("REDISPASSWORD", "secret");

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("spring.data.redis.url"))
                .isEqualTo("redis://appuser:secret@redis.example.internal:6379");
    }
}
