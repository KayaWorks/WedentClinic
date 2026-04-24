package com.wedent.clinic.company.dto;

/**
 * Tenant root view. Exposed only through {@code /api/companies/current} —
 * callers can never resolve any other company.
 */
public record CompanyResponse(
        Long id,
        String name,
        String taxNumber,
        String phone,
        String email
) {}
