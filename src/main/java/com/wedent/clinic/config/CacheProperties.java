package com.wedent.clinic.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.redis")
public record CacheProperties(Duration defaultTtl, String keyPrefix) {
    public CacheProperties {
        if (defaultTtl == null) defaultTtl = Duration.ofMinutes(10);
        if (keyPrefix == null || keyPrefix.isBlank()) keyPrefix = "wedent:";
    }
}
