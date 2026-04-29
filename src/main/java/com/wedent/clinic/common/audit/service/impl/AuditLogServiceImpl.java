package com.wedent.clinic.common.audit.service.impl;

import com.wedent.clinic.common.audit.dto.AuditLogDto;
import com.wedent.clinic.common.audit.entity.AuditLog;
import com.wedent.clinic.common.audit.repository.AuditLogRepository;
import com.wedent.clinic.common.audit.service.AuditLogService;
import com.wedent.clinic.common.dto.PageResponse;
import com.wedent.clinic.common.exception.ResourceNotFoundException;
import com.wedent.clinic.patient.entity.Patient;
import com.wedent.clinic.patient.repository.PatientRepository;
import com.wedent.clinic.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final PatientRepository patientRepository;

    @Override
    public PageResponse<AuditLogDto> listForPatient(Long patientId,
                                                     String eventType,
                                                     String targetType,
                                                     Pageable pageable) {
        Long companyId = SecurityUtils.currentCompanyId();

        // Verify patient exists in this tenant and caller has clinic access
        Patient patient = patientRepository.findByIdAndCompanyId(patientId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", patientId));
        SecurityUtils.verifyClinicAccess(patient.getClinic().getId());

        Page<AuditLogDto> page = auditLogRepository
                .findByPatient(companyId, patientId,
                        (eventType != null && !eventType.isBlank()) ? eventType : null,
                        (targetType != null && !targetType.isBlank()) ? targetType : null,
                        pageable)
                .map(AuditLogServiceImpl::toDto);

        return PageResponse.of(page);
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    private static AuditLogDto toDto(AuditLog a) {
        return new AuditLogDto(
                a.getId(),
                a.getEventType(),
                a.getActorUserId(),
                a.getPatientId(),
                a.getTargetType(),
                a.getTargetId(),
                a.getDetail(),
                a.getOccurredAt()
        );
    }
}
