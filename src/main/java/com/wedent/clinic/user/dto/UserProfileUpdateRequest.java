package com.wedent.clinic.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload for {@code PATCH /api/users/me}. Email/company/clinic are deliberately
 * out of scope: they require admin privileges + a tenant-aware flow and are
 * surfaced via the user-management endpoints (future work), not self-service.
 */
public record UserProfileUpdateRequest(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName
) {}
