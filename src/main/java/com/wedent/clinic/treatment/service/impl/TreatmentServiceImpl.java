package com.wedent.clinic.treatment.service.impl;

import com.wedent.clinic.common.audit.AuditEventPublisher;
import com.wedent.clinic.common.audit.event.AuditEvent;
import com.wedent.clinic.common.audit.event.AuditEventType;
import com.wedent.clinic.common.dto.PageResponse;
import com.wedent.clinic.common.exception.BusinessException;
import com.wedent.clinic.common.exception.ErrorCode;
import com.wedent.clinic.common.exception.ResourceNotFoundException;
import com.wedent.clinic.employee.entity.Employee;
import com.wedent.clinic.employee.entity.EmployeeStatus;
import com.wedent.clinic.employee.entity.EmployeeType;
import com.wedent.clinic.employee.repository.EmployeeRepository;
import com.wedent.clinic.patient.entity.Patient;
import com.wedent.clinic.patient.repository.PatientRepository;
import com.wedent.clinic.security.SecurityUtils;
import com.wedent.clinic.treatment.dto.TreatmentCreateRequest;
import com.wedent.clinic.treatment.dto.TreatmentResponse;
import com.wedent.clinic.treatment.dto.TreatmentUpdateRequest;
import com.wedent.clinic.treatment.entity.Treatment;
import com.wedent.clinic.treatment.entity.TreatmentStatus;
import com.wedent.clinic.treatment.mapper.TreatmentMapper;
import com.wedent.clinic.treatment.repository.TreatmentRepository;
import com.wedent.clinic.treatment.service.TreatmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Treatment lifecycle. Tenant + clinic scope is enforced via the patient:
 * a treatment always inherits {@code patient.company} and
 * {@code patient.clinic}, so cross-tenant contamination is impossible
 * even if the body tries to override it.
 *
 * <p>Doctor re-assignment / fee changes / status flips are blocked once
 * the treatment is payout-locked — that's the contract the payouts
 * module relies on to keep already-issued totals stable.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class TreatmentServiceImpl implements TreatmentService {

    private static final String DEFAULT_CURRENCY = "TRY";

    private final TreatmentRepository repository;
    private final PatientRepository patientRepository;
    private final EmployeeRepository employeeRepository;
    @Qualifier("treatmentMapperImpl")
    private final TreatmentMapper mapper;
    private final AuditEventPublisher auditEventPublisher;

    @Override
    public TreatmentResponse create(Long patientId, TreatmentCreateRequest request) {
        Long companyId = SecurityUtils.currentCompanyId();
        Patient patient = loadPatient(patientId, companyId);
        SecurityUtils.verifyClinicAccess(patient.getClinic().getId());
        Employee doctor = loadDoctor(request.doctorId(), companyId);

        Treatment entity = mapper.toEntity(request);
        entity.setPatient(patient);
        entity.setCompany(patient.getCompany());
        entity.setClinic(patient.getClinic());
        entity.setDoctor(doctor);
        if (entity.getCurrency() == null || entity.getCurrency().isBlank()) {
            entity.setCurrency(DEFAULT_CURRENCY);
        }
        if (entity.getStatus() == null) {
            entity.setStatus(TreatmentStatus.PLANNED);
        }
        // If the FE seeds a treatment already in COMPLETED (back-dated
        // entry), stamp completedAt now so payout aggregation picks it up.
        if (entity.getStatus() == TreatmentStatus.COMPLETED && entity.getCompletedAt() == null) {
            entity.setCompletedAt(Instant.now());
        }

        Treatment saved = repository.save(entity);

        auditEventPublisher.publish(AuditEvent.builder(AuditEventType.TREATMENT_CREATED)
                .actorUserId(SecurityUtils.currentUserIdOrNull())
                .companyId(companyId)
                .clinicId(patient.getClinic().getId())
                .patientId(patientId)
                .targetType("Treatment")
                .targetId(saved.getId())
                .detail(Map.of(
                        "patientId", patientId,
                        "doctorId", doctor.getId(),
                        "fee", saved.getFee(),
                        "status", saved.getStatus().name()))
                .build());

        return mapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<TreatmentResponse> listForPatient(Long patientId, Pageable pageable) {
        Long companyId = SecurityUtils.currentCompanyId();
        Patient patient = loadPatient(patientId, companyId);
        SecurityUtils.verifyClinicAccess(patient.getClinic().getId());

        Page<Treatment> page = repository.findByPatientIdAndCompanyId(patientId, companyId, pageable);
        return PageResponse.of(page.map(mapper::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public TreatmentResponse getById(Long id) {
        Treatment treatment = loadTreatmentInScope(id);
        return mapper.toResponse(treatment);
    }

    @Override
    public TreatmentResponse update(Long id, TreatmentUpdateRequest request) {
        Treatment treatment = loadTreatmentInScope(id);
        SecurityUtils.verifyClinicAccess(treatment.getClinic().getId());
        assertNotPayoutLocked(treatment);

        // Snapshot for the audit diff before any mutation.
        TreatmentStatus statusBefore = treatment.getStatus();
        BigDecimal feeBefore = treatment.getFee();
        Long doctorBefore = treatment.getDoctor().getId();

        // Doctor re-assignment is its own dance — needs an entity load +
        // tenant verification, so it's handled outside the mapper pass.
        if (request.doctorId() != null && !request.doctorId().equals(doctorBefore)) {
            Employee newDoctor = loadDoctor(request.doctorId(), treatment.getCompany().getId());
            treatment.setDoctor(newDoctor);
        }
        mapper.updateEntity(request, treatment);
        if (request.currency() != null && request.currency().isBlank()) {
            // Defensive: blank string would silently overwrite via the
            // mapper since IGNORE only kicks in for null. Keep TRY default.
            treatment.setCurrency(DEFAULT_CURRENCY);
        }
        // Status→COMPLETED transition stamps completedAt; going back to
        // PLANNED/CANCELLED clears it so the treatment falls out of payouts.
        if (statusBefore != TreatmentStatus.COMPLETED
                && treatment.getStatus() == TreatmentStatus.COMPLETED) {
            treatment.setCompletedAt(Instant.now());
        } else if (statusBefore == TreatmentStatus.COMPLETED
                && treatment.getStatus() != TreatmentStatus.COMPLETED) {
            treatment.setCompletedAt(null);
        }

        Map<String, Object> diff = new LinkedHashMap<>();
        diffField(diff, "doctorId", doctorBefore, treatment.getDoctor().getId());
        diffField(diff, "fee", feeBefore, treatment.getFee());
        diffField(diff, "status", statusBefore, treatment.getStatus());

        AuditEventType eventType = statusBefore != treatment.getStatus()
                ? AuditEventType.TREATMENT_STATUS_CHANGED
                : AuditEventType.TREATMENT_UPDATED;
        auditEventPublisher.publish(AuditEvent.builder(eventType)
                .actorUserId(SecurityUtils.currentUserIdOrNull())
                .companyId(treatment.getCompany().getId())
                .clinicId(treatment.getClinic().getId())
                .patientId(treatment.getPatient().getId())
                .targetType("Treatment")
                .targetId(treatment.getId())
                .detail(diff.isEmpty() ? Map.of("noop", true) : diff)
                .build());

        return mapper.toResponse(treatment);
    }

    @Override
    public void delete(Long id) {
        Treatment treatment = loadTreatmentInScope(id);
        SecurityUtils.verifyClinicAccess(treatment.getClinic().getId());
        assertNotPayoutLocked(treatment);

        repository.delete(treatment); // soft delete via @SQLDelete

        auditEventPublisher.publish(AuditEvent.builder(AuditEventType.TREATMENT_DELETED)
                .actorUserId(SecurityUtils.currentUserIdOrNull())
                .companyId(treatment.getCompany().getId())
                .clinicId(treatment.getClinic().getId())
                .patientId(treatment.getPatient().getId())
                .targetType("Treatment")
                .targetId(treatment.getId())
                .detail(Map.of(
                        "patientId", treatment.getPatient().getId(),
                        "doctorId", treatment.getDoctor().getId(),
                        "fee", treatment.getFee()))
                .build());
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    private Patient loadPatient(Long patientId, Long companyId) {
        return patientRepository.findByIdAndCompanyId(patientId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", patientId));
    }

    private Employee loadDoctor(Long doctorId, Long companyId) {
        Employee doctor = employeeRepository
                .findByIdAndCompanyIdAndEmployeeType(doctorId, companyId, EmployeeType.DOCTOR)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST,
                        "Doctor not found in this company: id=" + doctorId));
        if (doctor.getStatus() != EmployeeStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Selected doctor is not active");
        }
        return doctor;
    }

    private Treatment loadTreatmentInScope(Long id) {
        Long companyId = SecurityUtils.currentCompanyId();
        return repository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Treatment", id));
    }

    private void assertNotPayoutLocked(Treatment treatment) {
        if (treatment.isPayoutLocked()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Treatment is locked by an issued payout and cannot be modified");
        }
    }

    private static void diffField(Map<String, Object> diff, String key, Object before, Object after) {
        if (before == null ? after == null : before.equals(after)) return;
        Map<String, Object> entry = new HashMap<>();
        entry.put("from", before);
        entry.put("to", after);
        diff.put(key, entry);
    }
}
