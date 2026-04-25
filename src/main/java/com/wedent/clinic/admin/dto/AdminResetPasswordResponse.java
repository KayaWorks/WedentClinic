package com.wedent.clinic.admin.dto;

/**
 * One-shot carrier for an admin-generated temporary password. The plain
 * value is surfaced here and <em>nowhere else</em> — the server only
 * stores the bcrypt hash. Admin is expected to relay it out-of-band
 * (SMS, handoff, password manager).
 */
public record AdminResetPasswordResponse(String temporaryPassword) {}
