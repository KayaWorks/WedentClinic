package com.wedent.clinic.user.dto;

/**
 * Lookup view for a permission row. Exposes the stable {@code code}
 * (what the frontend compares against) alongside the human-readable
 * {@code description} for admin UIs.
 */
public record PermissionResponse(Long id, String code, String description) {}
