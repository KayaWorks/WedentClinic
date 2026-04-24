package com.wedent.clinic.treatment;

import com.wedent.clinic.clinic.entity.Clinic;
import com.wedent.clinic.common.audit.AuditEventPublisher;
import com.wedent.clinic.common.audit.event.AuditEvent;
import com.wedent.clinic.common.audit.event.AuditEventType;
import com.wedent.clinic.common.exception.BusinessException;
import com.wedent.clinic.common.exception.ErrorCode;
import com.wedent.clinic.common.exception.ResourceNotFoundException;
import com.wedent.clinic.company.entity.Company;
import com.wedent.clinic.employee.entity.Employee;
import com.wedent.clinic.employee.entity.EmployeeStatus;
import com.wedent.clinic.employee.entity.EmployeeType;
import com.wedent.clinic.employee.repository.EmployeeRepository;
import com.wedent.clinic.patient.entity.Patient;
import com.wedent.clinic.patient.repository.PatientRepository;
import com.wedent.clinic.security.AuthenticatedUser;
import com.wedent.clinic.treatment.dto.TreatmentCreateRequest;
import com.wedent.clinic.treatment.dto.TreatmentResponse;
import com.wedent.clinic.treatment.dto.TreatmentUpdateRequest;
import com.wedent.clinic.treatment.entity.Treatment;
import com.wedent.clinic.treatment.entity.TreatmentStatus;
import com.wedent.clinic.treatment.mapper.TreatmentMapper;
import com.wedent.clinic.treatment.repository.TreatmentRepository;
import com.wedent.clinic.treatment.service.impl.TreatmentServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TreatmentServiceImplTest {

    private static final Long COMPANY_ID = 100L;
    private static final Long CLINIC_ID = 10L;
    private static final Long PATIENT_ID = 200L;
    private static final Long DOCTOR_ID = 300L;

    private final TreatmentRepository treatmentRepository = Mockito.mock(TreatmentRepository.class);
    private final PatientRepository patientRepository = Mockito.mock(PatientRepository.class);
    private final EmployeeRepository employeeRepository = Mockito.mock(EmployeeRepository.class);
    private final TreatmentMapper mapper = Mockito.mock(TreatmentMapper.class);
    private final AuditEventPublisher auditEventPublisher = Mockito.mock(AuditEventPublisher.class);
    private final TreatmentServiceImpl service = new TreatmentServiceImpl(
            treatmentRepository, patientRepository, employeeRepository, mapper, auditEventPublisher);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    // ─── create ─────────────────────────────────────────────────────────────

    @Test
    void create_happyPath_wiresRelations_appliesDefaults_andAudits() {
        authenticate(7L, COMPANY_ID, CLINIC_ID, "MANAGER");
        Patient patient = patient();
        Employee doctor = doctor(EmployeeStatus.ACTIVE);
        when(patientRepository.findByIdAndCompanyId(PATIENT_ID, COMPANY_ID)).thenReturn(Optional.of(patient));
        when(employeeRepository.findByIdAndCompanyIdAndEmployeeType(DOCTOR_ID, COMPANY_ID, EmployeeType.DOCTOR))
                .thenReturn(Optional.of(doctor));

        // Mapper produces a bare entity — service is responsible for relations + defaults.
        Treatment partial = Treatment.builder()
                .name("Dolgu")
                .performedAt(Instant.parse("2026-04-20T10:00:00Z"))
                .fee(new BigDecimal("500.00"))
                .build();
        when(mapper.toEntity(any(TreatmentCreateRequest.class))).thenReturn(partial);
        when(treatmentRepository.save(any(Treatment.class))).thenAnswer(inv -> {
            Treatment t = inv.getArgument(0);
            t.setId(999L);
            return t;
        });
        when(mapper.toResponse(any(Treatment.class))).thenReturn(stubResponse(999L));

        TreatmentResponse response = service.create(PATIENT_ID, new TreatmentCreateRequest(
                DOCTOR_ID, "Dolgu", "16", "Composite filling",
                Instant.parse("2026-04-20T10:00:00Z"),
                new BigDecimal("500.00"), null, null));

        assertThat(response.id()).isEqualTo(999L);
        ArgumentCaptor<Treatment> savedCaptor = ArgumentCaptor.forClass(Treatment.class);
        verify(treatmentRepository).save(savedCaptor.capture());
        Treatment saved = savedCaptor.getValue();
        assertThat(saved.getPatient()).isSameAs(patient);
        assertThat(saved.getDoctor()).isSameAs(doctor);
        assertThat(saved.getCompany()).isSameAs(patient.getCompany());
        assertThat(saved.getClinic()).isSameAs(patient.getClinic());
        assertThat(saved.getCurrency()).isEqualTo("TRY");
        assertThat(saved.getStatus()).isEqualTo(TreatmentStatus.PLANNED);

        ArgumentCaptor<AuditEvent> evCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventPublisher).publish(evCaptor.capture());
        assertThat(evCaptor.getValue().type()).isEqualTo(AuditEventType.TREATMENT_CREATED);
        assertThat(evCaptor.getValue().detail())
                .containsEntry("doctorId", DOCTOR_ID)
                .containsEntry("patientId", PATIENT_ID);
    }

    @Test
    void create_doctorNotInCompany_throwsInvalidRequest() {
        authenticate(7L, COMPANY_ID, CLINIC_ID, "MANAGER");
        when(patientRepository.findByIdAndCompanyId(PATIENT_ID, COMPANY_ID)).thenReturn(Optional.of(patient()));
        when(employeeRepository.findByIdAndCompanyIdAndEmployeeType(DOCTOR_ID, COMPANY_ID, EmployeeType.DOCTOR))
                .thenReturn(Optional.empty());

        BusinessException ex = catchThrowableOfType(
                () -> service.create(PATIENT_ID, baseCreateRequest()),
                BusinessException.class);
        assertThat(ex).isNotNull();
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST);
        verify(treatmentRepository, never()).save(any());
        verify(auditEventPublisher, never()).publish(any());
    }

    @Test
    void create_doctorInactive_throwsBusinessRuleViolation() {
        authenticate(7L, COMPANY_ID, CLINIC_ID, "MANAGER");
        when(patientRepository.findByIdAndCompanyId(PATIENT_ID, COMPANY_ID)).thenReturn(Optional.of(patient()));
        when(employeeRepository.findByIdAndCompanyIdAndEmployeeType(DOCTOR_ID, COMPANY_ID, EmployeeType.DOCTOR))
                .thenReturn(Optional.of(doctor(EmployeeStatus.PASSIVE)));

        BusinessException ex = catchThrowableOfType(
                () -> service.create(PATIENT_ID, baseCreateRequest()),
                BusinessException.class);
        assertThat(ex).isNotNull();
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_RULE_VIOLATION);
    }

    @Test
    void create_patientMissing_throwsResourceNotFound() {
        authenticate(7L, COMPANY_ID, CLINIC_ID, "MANAGER");
        when(patientRepository.findByIdAndCompanyId(PATIENT_ID, COMPANY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(PATIENT_ID, baseCreateRequest()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ─── update ─────────────────────────────────────────────────────────────

    @Test
    void update_feeChange_emitsTreatmentUpdated_withFromTo() {
        authenticate(7L, COMPANY_ID, CLINIC_ID, "MANAGER");
        Treatment treatment = existingTreatment();
        when(treatmentRepository.findByIdAndCompanyId(999L, COMPANY_ID)).thenReturn(Optional.of(treatment));
        Mockito.doAnswer(inv -> {
            TreatmentUpdateRequest req = inv.getArgument(0);
            if (req.fee() != null) treatment.setFee(req.fee());
            return null;
        }).when(mapper).updateEntity(any(TreatmentUpdateRequest.class), any(Treatment.class));
        when(mapper.toResponse(treatment)).thenReturn(stubResponse(999L));

        service.update(999L, new TreatmentUpdateRequest(
                null, null, null, null, null, new BigDecimal("750.00"), null, null));

        ArgumentCaptor<AuditEvent> ev = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventPublisher).publish(ev.capture());
        assertThat(ev.getValue().type()).isEqualTo(AuditEventType.TREATMENT_UPDATED);
        Map<String, Object> diff = ev.getValue().detail();
        assertThat(diff).containsKey("fee");
        @SuppressWarnings("unchecked")
        Map<String, Object> feeEntry = (Map<String, Object>) diff.get("fee");
        assertThat(feeEntry).containsEntry("from", new BigDecimal("500.00"));
        assertThat(feeEntry).containsEntry("to", new BigDecimal("750.00"));
    }

    @Test
    void update_statusFlip_emitsStatusChangedEvent() {
        authenticate(7L, COMPANY_ID, CLINIC_ID, "MANAGER");
        Treatment treatment = existingTreatment();
        when(treatmentRepository.findByIdAndCompanyId(999L, COMPANY_ID)).thenReturn(Optional.of(treatment));
        Mockito.doAnswer(inv -> {
            TreatmentUpdateRequest req = inv.getArgument(0);
            if (req.status() != null) treatment.setStatus(req.status());
            return null;
        }).when(mapper).updateEntity(any(TreatmentUpdateRequest.class), any(Treatment.class));
        when(mapper.toResponse(treatment)).thenReturn(stubResponse(999L));

        service.update(999L, new TreatmentUpdateRequest(
                null, null, null, null, null, null, null, TreatmentStatus.COMPLETED));

        ArgumentCaptor<AuditEvent> ev = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventPublisher).publish(ev.capture());
        assertThat(ev.getValue().type()).isEqualTo(AuditEventType.TREATMENT_STATUS_CHANGED);
    }

    @Test
    void update_payoutLocked_rejectedWithBusinessRule() {
        authenticate(7L, COMPANY_ID, CLINIC_ID, "MANAGER");
        Treatment treatment = existingTreatment();
        treatment.setPayoutLockedAt(Instant.parse("2026-04-21T00:00:00Z"));
        when(treatmentRepository.findByIdAndCompanyId(999L, COMPANY_ID)).thenReturn(Optional.of(treatment));

        BusinessException ex = catchThrowableOfType(
                () -> service.update(999L, new TreatmentUpdateRequest(
                        null, null, null, null, null, new BigDecimal("750.00"), null, null)),
                BusinessException.class);
        assertThat(ex).isNotNull();
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_RULE_VIOLATION);
        verify(auditEventPublisher, never()).publish(any());
    }

    @Test
    void update_doctorReassignment_loadsNewEmployee_andSwapsRelation() {
        authenticate(7L, COMPANY_ID, CLINIC_ID, "MANAGER");
        Treatment treatment = existingTreatment();
        when(treatmentRepository.findByIdAndCompanyId(999L, COMPANY_ID)).thenReturn(Optional.of(treatment));
        Employee replacement = Employee.builder()
                .firstName("Yeni").lastName("Hekim")
                .employeeType(EmployeeType.DOCTOR).status(EmployeeStatus.ACTIVE)
                .company(treatment.getCompany()).clinic(treatment.getClinic())
                .build();
        replacement.setId(401L);
        when(employeeRepository.findByIdAndCompanyIdAndEmployeeType(401L, COMPANY_ID, EmployeeType.DOCTOR))
                .thenReturn(Optional.of(replacement));
        when(mapper.toResponse(treatment)).thenReturn(stubResponse(999L));

        service.update(999L, new TreatmentUpdateRequest(
                401L, null, null, null, null, null, null, null));

        assertThat(treatment.getDoctor()).isSameAs(replacement);
    }

    // ─── delete ─────────────────────────────────────────────────────────────

    @Test
    void delete_happyPath_softDeletesAndAudits() {
        authenticate(7L, COMPANY_ID, CLINIC_ID, "MANAGER");
        Treatment treatment = existingTreatment();
        when(treatmentRepository.findByIdAndCompanyId(999L, COMPANY_ID)).thenReturn(Optional.of(treatment));

        service.delete(999L);

        verify(treatmentRepository).delete(treatment);
        ArgumentCaptor<AuditEvent> ev = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventPublisher).publish(ev.capture());
        assertThat(ev.getValue().type()).isEqualTo(AuditEventType.TREATMENT_DELETED);
    }

    @Test
    void delete_payoutLocked_rejectedWithBusinessRule() {
        authenticate(7L, COMPANY_ID, CLINIC_ID, "MANAGER");
        Treatment treatment = existingTreatment();
        treatment.setPayoutLockedAt(Instant.now());
        when(treatmentRepository.findByIdAndCompanyId(999L, COMPANY_ID)).thenReturn(Optional.of(treatment));

        BusinessException ex = catchThrowableOfType(() -> service.delete(999L), BusinessException.class);
        assertThat(ex).isNotNull();
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_RULE_VIOLATION);
        verify(treatmentRepository, never()).delete(any(Treatment.class));
    }

    // ─── builders ──────────────────────────────────────────────────────────

    private static Patient patient() {
        Company company = Company.builder().name("Acme").build();
        company.setId(COMPANY_ID);
        Clinic clinic = Clinic.builder().name("Main").company(company).build();
        clinic.setId(CLINIC_ID);
        Patient p = Patient.builder()
                .firstName("Ali").lastName("Veli").phone("+905550000000")
                .company(company).clinic(clinic)
                .build();
        p.setId(PATIENT_ID);
        return p;
    }

    private static Employee doctor(EmployeeStatus status) {
        Patient p = patient();
        Employee d = Employee.builder()
                .firstName("Ayşe").lastName("Demir")
                .email("ayse@acme.com")
                .employeeType(EmployeeType.DOCTOR).status(status)
                .company(p.getCompany()).clinic(p.getClinic())
                .build();
        d.setId(DOCTOR_ID);
        return d;
    }

    private static Treatment existingTreatment() {
        Patient p = patient();
        Employee d = doctor(EmployeeStatus.ACTIVE);
        Treatment t = Treatment.builder()
                .patient(p).doctor(d)
                .company(p.getCompany()).clinic(p.getClinic())
                .name("Dolgu").performedAt(Instant.parse("2026-04-20T10:00:00Z"))
                .fee(new BigDecimal("500.00")).currency("TRY")
                .status(TreatmentStatus.PLANNED)
                .build();
        t.setId(999L);
        return t;
    }

    private static TreatmentCreateRequest baseCreateRequest() {
        return new TreatmentCreateRequest(
                DOCTOR_ID, "Dolgu", null, null,
                Instant.parse("2026-04-20T10:00:00Z"),
                new BigDecimal("500.00"), null, null);
    }

    private static TreatmentResponse stubResponse(Long id) {
        return new TreatmentResponse(
                id, PATIENT_ID, DOCTOR_ID, "Ayşe Demir",
                CLINIC_ID, COMPANY_ID, "Dolgu", "16", "n",
                Instant.parse("2026-04-20T10:00:00Z"),
                new BigDecimal("500.00"), "TRY",
                TreatmentStatus.PLANNED, null);
    }

    private static void authenticate(Long userId, Long companyId, Long clinicId, String role) {
        AuthenticatedUser principal = new AuthenticatedUser(
                userId, "user@example.com", companyId, clinicId, Set.of(role), List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.authorities()));
    }
}
