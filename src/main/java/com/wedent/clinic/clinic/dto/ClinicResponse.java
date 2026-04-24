package com.wedent.clinic.clinic.dto;

/**
 * Lookup view for a clinic. Flat — no nested company object — because the
 * company is always the caller's own tenant root (inferred from the session)
 * and returning it again would just be noise.
 */
public record ClinicResponse(
        Long id,
        Long companyId,
        String name,
        String address,
        String phone,
        String email
) {}
