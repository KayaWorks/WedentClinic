package com.wedent.clinic.clinic.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Owner-only payload for provisioning a new clinic under the caller's company.
 * Company id is not a field — it is always derived from the caller's tenant
 * root to stop a forged body from landing a clinic under the wrong tenant.
 */
public record ClinicCreateRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 500) String address,
        @Size(max = 30) String phone,
        @Email @Size(max = 150) String email
) {}
