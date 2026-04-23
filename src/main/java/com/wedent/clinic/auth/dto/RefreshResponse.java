package com.wedent.clinic.auth.dto;

public record RefreshResponse(
        String accessToken,
        String tokenType,
        long expiresInMillis,
        String refreshToken,
        long refreshExpiresInMillis
) {}
