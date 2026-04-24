package com.wedent.clinic.company.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * Partial update for the caller's own company. All fields nullable; the mapper
 * skips nulls so omitted fields stay intact. {@code taxNumber} uniqueness is
 * enforced at the DB level — the service surfaces the clash as a 409 before
 * the save hits the unique index.
 */
public record CompanyUpdateRequest(
        @Size(max = 200) String name,
        @Size(max = 50) String taxNumber,
        @Size(max = 30) String phone,
        @Email @Size(max = 150) String email
) {}
