package com.wedent.clinic.auth.dto;

import java.util.Set;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresInMillis,
        Long userId,
        String email,
        String firstName,
        String lastName,
        Long companyId,
        Long clinicId,
        Set<String> roles
) {}
