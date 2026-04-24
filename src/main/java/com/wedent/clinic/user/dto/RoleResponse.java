package com.wedent.clinic.user.dto;

import com.wedent.clinic.user.entity.RoleType;

import java.util.List;

/**
 * Lookup view for a role. Permissions are eagerly inlined because role
 * administration UIs always want to show "what this role can do" next to
 * the role itself — saving a second round-trip is worth the slight payload
 * growth given the closed set is small (4 roles × ~dozen permissions each).
 */
public record RoleResponse(
        Long id,
        RoleType code,
        String description,
        List<PermissionResponse> permissions
) {}
