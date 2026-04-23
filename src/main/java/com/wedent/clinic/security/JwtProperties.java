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
            /**
             * Refresh-token lifetime in days.  Defaults to 14 if not set —
             * matches typical "remember-me for two weeks" UX while keeping
             * the blast radius of a leaked token bounded.
             */
            Long refreshTokenExpirationDays,
            String issuer
    ) {
        public long refreshTokenExpirationDaysOrDefault() {
            return refreshTokenExpirationDays == null ? 14L : refreshTokenExpirationDays;
        }
    }
}
