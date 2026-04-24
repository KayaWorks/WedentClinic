package com.wedent.clinic.treatment.service;

import com.wedent.clinic.common.dto.PageResponse;
import com.wedent.clinic.treatment.dto.TreatmentCreateRequest;
import com.wedent.clinic.treatment.dto.TreatmentResponse;
import com.wedent.clinic.treatment.dto.TreatmentUpdateRequest;
import org.springframework.data.domain.Pageable;

/**
 * Patient-profile treatment log. Every mutation is tenant-scoped and
 * checks the payout-lock before allowing edits, so the payout module's
 * already-issued totals can never silently shift under it.
 */
public interface TreatmentService {

    TreatmentResponse create(Long patientId, TreatmentCreateRequest request);

    PageResponse<TreatmentResponse> listForPatient(Long patientId, Pageable pageable);

    TreatmentResponse getById(Long id);

    TreatmentResponse update(Long id, TreatmentUpdateRequest request);

    void delete(Long id);
}
