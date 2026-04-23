package com.wedent.clinic.user.dto;

import com.wedent.clinic.user.entity.UserStatus;

import java.time.Instant;
import java.util.Set;

/**
 * Read-model for {@code GET /api/users/me}.
 *
 * <p>Roles + permissions are flattened to string codes so the frontend can
 * drive UI guards without a second round-trip. The audit fields
 * ({@code createdAt}/{@code updatedAt}) are present so the profile page can
 * show "member since" without hitting any other endpoint.</p>
 */
public record UserProfileResponse(
        Long id,
        String email,
        String firstName,
        String lastName,
        UserStatus status,
        Long companyId,
        String companyName,
        Long clinicId,
        String clinicName,
        Set<String> roles,
        Set<String> permissions,
        Instant createdAt,
        Instant updatedAt
) {}
