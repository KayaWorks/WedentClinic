package com.wedent.clinic.note.service;

import com.wedent.clinic.common.dto.PageResponse;
import com.wedent.clinic.note.dto.PatientNoteCreateRequest;
import com.wedent.clinic.note.dto.PatientNoteResponse;
import com.wedent.clinic.note.dto.PatientNoteUpdateRequest;
import org.springframework.data.domain.Pageable;

public interface PatientNoteService {

    PatientNoteResponse create(Long patientId, PatientNoteCreateRequest request);

    PageResponse<PatientNoteResponse> listForPatient(Long patientId, Pageable pageable);

    PatientNoteResponse update(Long noteId, PatientNoteUpdateRequest request);

    void delete(Long noteId);
}
