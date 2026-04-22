package com.wedent.clinic.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app.security")
public record JwtProperties(
        Jwt jwt,
        List<String> publicPaths
) {

    public record Jwt(
            String secret,
            long accessTokenExpirationMinutes,
            String issuer
    ) {}
}
