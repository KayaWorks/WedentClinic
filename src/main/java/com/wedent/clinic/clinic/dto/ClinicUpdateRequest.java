package com.wedent.clinic.clinic.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * Partial update — every field is nullable and the mapper uses
 * {@code NullValuePropertyMappingStrategy.IGNORE} so omitted fields don't
 * clobber existing values. {@code name} has no {@code @NotBlank} for that
 * same reason; service-layer validation rejects an explicit empty string.
 */
public record ClinicUpdateRequest(
        @Size(max = 200) String name,
        @Size(max = 500) String address,
        @Size(max = 30) String phone,
        @Email @Size(max = 150) String email
) {}
