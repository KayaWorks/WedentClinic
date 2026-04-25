package com.wedent.clinic.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

import java.util.Map;

public class RedisUrlEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String PROPERTY_SOURCE_NAME = "wedentRedisUrl";
    private static final String SPRING_REDIS_URL = "spring.data.redis.url";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String redisUrl = first(environment, "REDIS_URL", "REDIS_PUBLIC_URL");
        if (!StringUtils.hasText(redisUrl)) {
            redisUrl = buildUrlFromSplitRailwayVariables(environment);
        }
        if (StringUtils.hasText(redisUrl)) {
            environment.getPropertySources().addFirst(new MapPropertySource(
                    PROPERTY_SOURCE_NAME,
                    Map.of(SPRING_REDIS_URL, redisUrl.trim())));
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    private static String buildUrlFromSplitRailwayVariables(ConfigurableEnvironment environment) {
        String host = first(environment, "REDISHOST", "REDIS_HOST");
        String password = first(environment, "REDISPASSWORD", "REDIS_PASSWORD");
        if (!StringUtils.hasText(host) || !StringUtils.hasText(password)) {
            return null;
        }

        String username = first(environment, "REDISUSER", "REDIS_USERNAME");
        if (!StringUtils.hasText(username)) {
            username = "default";
        }
        String port = first(environment, "REDISPORT", "REDIS_PORT");
        if (!StringUtils.hasText(port)) {
            port = "6379";
        }

        return "redis://%s:%s@%s:%s".formatted(username.trim(), password.trim(), host.trim(), port.trim());
    }

    private static String first(ConfigurableEnvironment environment, String... names) {
        for (String name : names) {
            String value = environment.getProperty(name);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }
}
