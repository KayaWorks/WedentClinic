package com.wedent.clinic.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(
        @NotBlank
        @Schema(description = "Opaque refresh token issued at /api/auth/login or a previous /api/auth/refresh",
                example = "bX9sY1h4c3....")
        String refreshToken
) {}
