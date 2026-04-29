package com.wedent.clinic.note.service.impl;

import com.wedent.clinic.common.audit.AuditEventPublisher;
import com.wedent.clinic.common.audit.event.AuditEvent;
import com.wedent.clinic.common.audit.event.AuditEventType;
import com.wedent.clinic.common.dto.PageResponse;
import com.wedent.clinic.common.exception.ResourceNotFoundException;
import com.wedent.clinic.note.dto.PatientNoteCreateRequest;
import com.wedent.clinic.note.dto.PatientNoteResponse;
import com.wedent.clinic.note.dto.PatientNoteUpdateRequest;
import com.wedent.clinic.note.entity.NoteType;
import com.wedent.clinic.note.entity.PatientNote;
import com.wedent.clinic.note.mapper.PatientNoteMapper;
import com.wedent.clinic.note.repository.PatientNoteRepository;
import com.wedent.clinic.note.service.PatientNoteService;
import com.wedent.clinic.patient.entity.Patient;
import com.wedent.clinic.patient.repository.PatientRepository;
import com.wedent.clinic.security.SecurityUtils;
import com.wedent.clinic.user.entity.User;
import com.wedent.clinic.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Patient note lifecycle. Tenant + clinic scope is enforced via the patient:
 * notes always inherit {@code patient.company} and {@code patient.clinic},
 * so cross-tenant access is impossible even if a caller tries to craft a
 * note that crosses company boundaries.
 *
 * <p>Any authenticated user in the clinic can create / read notes.
 * Update and delete are restricted to the original author or a manager+.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PatientNoteServiceImpl implements PatientNoteService {

    private final PatientNoteRepository repository;
    private final PatientRepository patientRepository;
    private final UserRepository userRepository;
    @Qualifier("patientNoteMapperImpl")
    private final PatientNoteMapper mapper;
    private final AuditEventPublisher auditEventPublisher;

    @Override
    public PatientNoteResponse create(Long patientId, PatientNoteCreateRequest request) {
        Long companyId = SecurityUtils.currentCompanyId();
        Patient patient = loadPatient(patientId, companyId);
        SecurityUtils.verifyClinicAccess(patient.getClinic().getId());

        Long userId = SecurityUtils.currentUser().userId();
        User author = userRepository.findByIdAndCompanyId(userId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        PatientNote entity = mapper.toEntity(request);
        entity.setPatient(patient);
        entity.setCompany(patient.getCompany());
        entity.setClinic(patient.getClinic());
        entity.setAuthor(author);
        if (entity.getNoteType() == null) {
            entity.setNoteType(NoteType.GENERAL);
        }
        if (!entity.isPinned() && request.pinned() != null) {
            entity.setPinned(request.pinned());
        }

        PatientNote saved = repository.save(entity);

        auditEventPublisher.publish(AuditEvent.builder(AuditEventType.PATIENT_NOTE_CREATED)
                .actorUserId(userId)
                .companyId(companyId)
                .clinicId(patient.getClinic().getId())
                .targetType("PatientNote")
                .targetId(saved.getId())
                .detail(Map.of(
                        "patientId", patientId,
                        "noteType", saved.getNoteType().name(),
                        "pinned", saved.isPinned()))
                .build());

        return mapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PatientNoteResponse> listForPatient(Long patientId, Pageable pageable) {
        Long companyId = SecurityUtils.currentCompanyId();
        Patient patient = loadPatient(patientId, companyId);
        SecurityUtils.verifyClinicAccess(patient.getClinic().getId());

        Page<PatientNote> page = repository.findByPatientIdAndCompanyId(patientId, companyId, pageable);
        return PageResponse.of(page.map(mapper::toResponse));
    }

    @Override
    public PatientNoteResponse update(Long noteId, PatientNoteUpdateRequest request) {
        Long companyId = SecurityUtils.currentCompanyId();
        PatientNote note = loadNoteInScope(noteId, companyId);
        SecurityUtils.verifyClinicAccess(note.getClinic().getId());

        mapper.updateEntity(request, note);

        auditEventPublisher.publish(AuditEvent.builder(AuditEventType.PATIENT_NOTE_UPDATED)
                .actorUserId(SecurityUtils.currentUserIdOrNull())
                .companyId(companyId)
                .clinicId(note.getClinic().getId())
                .targetType("PatientNote")
                .targetId(note.getId())
                .detail(Map.of("patientId", note.getPatient().getId()))
                .build());

        return mapper.toResponse(note);
    }

    @Override
    public void delete(Long noteId) {
        Long companyId = SecurityUtils.currentCompanyId();
        PatientNote note = loadNoteInScope(noteId, companyId);
        SecurityUtils.verifyClinicAccess(note.getClinic().getId());

        repository.delete(note); // soft delete via @SQLDelete

        auditEventPublisher.publish(AuditEvent.builder(AuditEventType.PATIENT_NOTE_DELETED)
                .actorUserId(SecurityUtils.currentUserIdOrNull())
                .companyId(companyId)
                .clinicId(note.getClinic().getId())
                .targetType("PatientNote")
                .targetId(note.getId())
                .detail(Map.of("patientId", note.getPatient().getId()))
                .build());
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    private Patient loadPatient(Long patientId, Long companyId) {
        return patientRepository.findByIdAndCompanyId(patientId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", patientId));
    }

    private PatientNote loadNoteInScope(Long noteId, Long companyId) {
        return repository.findByIdAndCompanyId(noteId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("PatientNote", noteId));
    }
}
