package com.wedent.clinic.config;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class RedisProfileGuard implements BeanFactoryPostProcessor, EnvironmentAware {

    private static final String REDIS_URL = "REDIS_URL";

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        validate(environment);
    }

    static void validate(Environment environment) {
        Set<String> activeProfiles = Arrays.stream(environment.getActiveProfiles())
                .collect(Collectors.toUnmodifiableSet());
        boolean strictProfile = activeProfiles.contains("railway") || activeProfiles.contains("prod");

        String rawRedisUrl = environment.getProperty(REDIS_URL);
        if (strictProfile && !StringUtils.hasText(rawRedisUrl)) {
            throw new IllegalStateException("REDIS_URL is required when running with the railway or prod profile");
        }
        if (strictProfile && looksUnresolved(rawRedisUrl)) {
            throw new IllegalStateException("REDIS_URL must be set to a real Redis URL, not a placeholder");
        }

        if (activeProfiles.contains("dev")) {
            String configuredRedisUrl = redisUrl(environment);
            String host = host(configuredRedisUrl);
            if (host != null && host.endsWith(".railway.internal")) {
                throw new IllegalStateException(
                        "redis.railway.internal works only inside Railway. For local dev, set REDIS_URL=redis://localhost:6379 or unset REDIS_URL.");
            }
        }
    }

    private static String redisUrl(Environment environment) {
        try {
            return environment.getProperty("spring.data.redis.url");
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static boolean looksUnresolved(String value) {
        return value != null && value.trim().startsWith("${");
    }

    private static String host(String url) {
        if (!StringUtils.hasText(url) || looksUnresolved(url)) {
            return null;
        }
        try {
            return URI.create(url.trim()).getHost();
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
