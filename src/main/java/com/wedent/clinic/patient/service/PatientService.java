package com.wedent.clinic.patient.service;

import com.wedent.clinic.common.dto.PageResponse;
import com.wedent.clinic.patient.dto.PatientCreateRequest;
import com.wedent.clinic.patient.dto.PatientResponse;
import com.wedent.clinic.patient.dto.PatientUpdateRequest;
import org.springframework.data.domain.Pageable;

public interface PatientService {

    PatientResponse create(PatientCreateRequest request);

    PatientResponse update(Long id, PatientUpdateRequest request);

    PatientResponse getById(Long id);

    PageResponse<PatientResponse> search(Long clinicId, String name, String phone, Pageable pageable);

    void delete(Long id);
}
