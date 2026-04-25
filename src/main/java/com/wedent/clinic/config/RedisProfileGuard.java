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

    private static final String REDIS_URL_PROPERTY = "spring.data.redis.url";

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

        String configuredRedisUrl = redisUrl(environment);
        if (strictProfile && !StringUtils.hasText(configuredRedisUrl)) {
            throw new IllegalStateException(activeProfiles.contains("railway")
                    ? "REDIS_URL, REDIS_PUBLIC_URL, or Railway split Redis variables are required for Railway profile"
                    : "spring.data.redis.url is required for prod profile");
        }
        if (strictProfile && looksUnresolved(configuredRedisUrl)) {
            throw new IllegalStateException("spring.data.redis.url must be set to a real Redis URL, not a placeholder");
        }
        String configuredHost = host(configuredRedisUrl);
        if (strictProfile && !StringUtils.hasText(configuredHost)) {
            throw new IllegalStateException("spring.data.redis.url must include a Redis host");
        }
        if (strictProfile && isLocalhost(configuredHost)) {
            throw new IllegalStateException("spring.data.redis.url must not point to localhost for railway/prod profiles");
        }

        if (activeProfiles.contains("dev")) {
            if (configuredHost != null && configuredHost.endsWith(".railway.internal")) {
                throw new IllegalStateException(
                        "Railway private Redis hosts work only inside Railway. Remove the Railway REDIS_URL from non-Railway profiles.");
            }
        }
    }

    private static String redisUrl(Environment environment) {
        return property(environment, REDIS_URL_PROPERTY);
    }

    private static String property(Environment environment, String name) {
        try {
            return environment.getProperty(name);
        } catch (IllegalArgumentException ex) {
            return "${" + name + "}";
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

    private static boolean isLocalhost(String host) {
        return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host);
    }
}
