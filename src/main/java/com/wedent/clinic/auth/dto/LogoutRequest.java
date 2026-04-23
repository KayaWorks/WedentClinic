package com.wedent.clinic.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record LogoutRequest(
        @NotBlank
        @Schema(description = "The refresh token to revoke. Access token keeps working until its natural expiry.")
        String refreshToken
) {}
