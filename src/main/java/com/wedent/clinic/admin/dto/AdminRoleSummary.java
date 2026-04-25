package com.wedent.clinic.admin.dto;

import com.wedent.clinic.user.entity.RoleType;

/**
 * Slim role projection for the admin list/detail responses. Only the
 * fields the UI actually renders — full role + permission payload stays
 * behind {@code /api/roles}.
 */
public record AdminRoleSummary(Long id, RoleType code) {}
