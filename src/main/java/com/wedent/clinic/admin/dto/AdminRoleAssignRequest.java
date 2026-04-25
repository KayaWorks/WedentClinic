package com.wedent.clinic.admin.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

/**
 * Bulk role assignment payload. The service appends — it does not
 * replace — so admins can incrementally grant without having to
 * reconcile the existing set client-side.
 */
public record AdminRoleAssignRequest(
        @NotEmpty Set<@NotNull Long> roleIds
) {}
