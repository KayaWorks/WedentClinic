package com.wedent.clinic.patient.service;

import com.wedent.clinic.common.dto.PageResponse;
import com.wedent.clinic.patient.dto.PatientCreateRequest;
import com.wedent.clinic.patient.dto.PatientResponse;
import com.wedent.clinic.patient.dto.PatientSummaryResponse;
import com.wedent.clinic.patient.dto.PatientUpdateRequest;
import org.springframework.data.domain.Pageable;

public interface PatientService {

    PatientResponse create(PatientCreateRequest request);

    PatientResponse update(Long id, PatientUpdateRequest request);

    PatientResponse getById(Long id);

    PageResponse<PatientResponse> search(Long clinicId, String name, String phone, Pageable pageable);

    /**
     * Scope-aware count for dashboards / tiles. Owners may query any clinic in
     * the company (or pass {@code null} for a company-wide total); non-owners
     * are always clamped to their own clinic by {@code TenantScopeResolver}.
     */
    long count(Long clinicId);

    void delete(Long id);

    /**
     * Single-query aggregated snapshot: treatment counts by status, fee total,
     * payment totals and balance.  Avoids the FE having to fetch full treatment
     * and payment lists just to render the overview card.
     */
    PatientSummaryResponse getSummary(Long id);
}
