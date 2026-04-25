package com.wedent.clinic.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app.cors")
public record AppCorsProperties(
        List<String> allowedOrigins
) {

    private static final List<String> DEFAULT_ALLOWED_ORIGINS = List.of(
            "http://localhost:5173",
            "https://clinicflow-dashboard-production.up.railway.app",
            "http://127.0.0.1:5173",
            "http://localhost:3000",
            "http://127.0.0.1:3000"
    );

    public List<String> allowedOriginsOrDefault() {
        if (allowedOrigins == null || allowedOrigins.isEmpty()) {
            return DEFAULT_ALLOWED_ORIGINS;
        }
        List<String> normalized = allowedOrigins.stream()
                .filter(origin -> origin != null && !origin.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        return normalized.isEmpty() ? DEFAULT_ALLOWED_ORIGINS : normalized;
    }
}
