package com.wedent.clinic.company.service;

import com.wedent.clinic.company.dto.CompanyResponse;
import com.wedent.clinic.company.dto.CompanyUpdateRequest;

/**
 * Single-tenant company lookup + owner-only profile update. No list endpoint
 * — cross-tenant enumeration has no legitimate use case — and no create /
 * delete since tenant provisioning still goes through admin seed migrations.
 */
public interface CompanyService {

    CompanyResponse getCurrent();

    CompanyResponse updateCurrent(CompanyUpdateRequest request);
}
