package com.wedent.clinic.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Self-service password change payload.
 *
 * <p>The regex enforces the minimum complexity the frontend already validates:
 * at least one lower-case, one upper-case, one digit, and one symbol. Keeping
 * the check server-side too means curl users can't sidestep it.</p>
 */
public record PasswordChangeRequest(
        @NotBlank String currentPassword,

        @NotBlank
        @Size(min = 8, max = 100,
                message = "New password must be between 8 and 100 characters")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^a-zA-Z0-9]).+$",
                message = "New password must contain upper, lower, digit and symbol"
        )
        String newPassword
) {}
