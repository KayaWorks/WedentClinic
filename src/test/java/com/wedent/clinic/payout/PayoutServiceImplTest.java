package com.wedent.clinic.payout;

import com.wedent.clinic.clinic.entity.Clinic;
import com.wedent.clinic.common.audit.AuditEventPublisher;
import com.wedent.clinic.common.audit.event.AuditEvent;
import com.wedent.clinic.common.audit.event.AuditEventType;
import com.wedent.clinic.common.exception.BusinessException;
import com.wedent.clinic.common.exception.ErrorCode;
import com.wedent.clinic.common.exception.ResourceNotFoundException;
import com.wedent.clinic.company.entity.Company;
import com.wedent.clinic.employee.entity.DoctorProfile;
import com.wedent.clinic.employee.entity.Employee;
import com.wedent.clinic.employee.entity.EmployeeStatus;
import com.wedent.clinic.employee.entity.EmployeeType;
import com.wedent.clinic.employee.repository.DoctorProfileRepository;
import com.wedent.clinic.patient.entity.Patient;
import com.wedent.clinic.payout.dto.PayoutDeductionRequest;
import com.wedent.clinic.payout.dto.PayoutDraftRequest;
import com.wedent.clinic.payout.entity.PayoutDeduction;
import com.wedent.clinic.payout.entity.PayoutDeductionType;
import com.wedent.clinic.payout.entity.PayoutPeriod;
import com.wedent.clinic.payout.entity.PayoutStatus;
import com.wedent.clinic.payout.mapper.PayoutMapper;
import com.wedent.clinic.payout.repository.PayoutDeductionRepository;
import com.wedent.clinic.payout.repository.PayoutPeriodRepository;
import com.wedent.clinic.payout.service.PayoutCalculator;
import com.wedent.clinic.payout.service.impl.PayoutServiceImpl;
import com.wedent.clinic.security.AuthenticatedUser;
import com.wedent.clinic.treatment.entity.Treatment;
import com.wedent.clinic.treatment.entity.TreatmentStatus;
import com.wedent.clinic.treatment.repository.TreatmentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the lifecycle guards, calculator integration, and
 * treatment-locking invariant on approve. Mapper is mocked as a
 * passthrough — its own tests live alongside {@link PayoutMapper}.
 */
class PayoutServiceImplTest {

    private static final Long COMPANY_ID = 100L;
    private static final Long CLINIC_ID = 10L;
    private static final Long DOCTOR_EMPLOYEE_ID = 300L;
    private static final Long DOCTOR_PROFILE_ID = 301L;

    private final PayoutPeriodRepository periodRepository = Mockito.mock(PayoutPeriodRepository.class);
    private final PayoutDeductionRepository deductionRepository = Mockito.mock(PayoutDeductionRepository.class);
    private final DoctorProfileRepository doctorProfileRepository = Mockito.mock(DoctorProfileRepository.class);
    private final TreatmentRepository treatmentRepository = Mockito.mock(TreatmentRepository.class);
    private final PayoutCalculator calculator = new PayoutCalculator(); // real helper
    private final PayoutMapper mapper = new PayoutMapper();              // real helper

    private final AuditEventPublisher auditEventPublisher = Mockito.mock(AuditEventPublisher.class);

    private final PayoutServiceImpl service = new PayoutServiceImpl(
            periodRepository, deductionRepository, doctorProfileRepository,
            treatmentRepository, calculator, mapper, auditEventPublisher);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    // ─── createDraft ────────────────────────────────────────────────────────

    @Test
    void createDraft_happyPath_buildsRow_runsCalculator_audits() {
        authenticate(7L, COMPANY_ID, CLINIC_ID, "CLINIC_OWNER");
        DoctorProfile profile = doctorProfile(new BigDecimal("25.00"), EmployeeStatus.ACTIVE);
        when(doctorProfileRepository.findById(DOCTOR_PROFILE_ID)).thenReturn(Optional.of(profile));

        Treatment t1 = treatment(1L, "500.00");
        Treatment t2 = treatment(2L, "1500.00");
        when(treatmentRepository.findEligibleForPayout(
                eq(DOCTOR_EMPLOYEE_ID), eq(COMPANY_ID),
                eq(TreatmentStatus.COMPLETED), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(t1, t2));

        AtomicLong idSeq = new AtomicLong(900L);
        when(periodRepository.save(any(PayoutPeriod.class))).thenAnswer(inv -> {
            PayoutPeriod p = inv.getArgument(0);
            p.setId(idSeq.getAndIncrement());
            return p;
        });

        var response = service.createDraft(new PayoutDraftRequest(
                DOCTOR_PROFILE_ID, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 5, 1)));

        assertThat(response.id()).isEqualTo(900L);
        assertThat(response.status()).isEqualTo(PayoutStatus.DRAFT);
        // 2000 * 25% = 500.00
        assertThat(response.grossAmount()).isEqualByComparingTo("500.00");
        assertThat(response.netAmount()).isEqualByComparingTo("500.00");

        ArgumentCaptor<AuditEvent> ev = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventPublisher).publish(ev.capture());
        assertThat(ev.getValue().type()).isEqualTo(AuditEventType.PAYOUT_DRAFT_CREATED);
        assertThat(ev.getValue().detail()).containsEntry("eligibleTreatmentCount", 2);
    }

    @Test
    void createDraft_startNotBeforeEnd_rejectedInvalidRequest() {
        authenticate(7L, COMPANY_ID, CLINIC_ID, "CLINIC_OWNER");
        BusinessException ex = catchThrowableOfType(
                () -> service.createDraft(new PayoutDraftRequest(
                        DOCTOR_PROFILE_ID, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 4, 1))),
                BusinessException.class);
        assertThat(ex).isNotNull();
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST);
        verify(periodRepository, never()).save(any());
    }

    @Test
    void createDraft_doctorNotInTenant_notFound_notLeaked() {
        authenticate(7L, COMPANY_ID, CLINIC_ID, "CLINIC_OWNER");
        DoctorProfile wrongTenant = doctorProfile(new BigDecimal("25.00"), EmployeeStatus.ACTIVE);
        // Swap the employee company so scope check fails.
        Company other = Company.builder().name("Other").build();
        other.setId(9999L);
        wrongTenant.getEmployee().setCompany(other);
        when(doctorProfileRepository.findById(DOCTOR_PROFILE_ID)).thenReturn(Optional.of(wrongTenant));

        assertThat(catchThrowableOfType(() -> service.createDraft(new PayoutDraftRequest(
                DOCTOR_PROFILE_ID, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 5, 1))),
                ResourceNotFoundException.class)).isNotNull();
    }

    @Test
    void createDraft_inactiveDoctor_businessRuleViolation() {
        authenticate(7L, COMPANY_ID, CLINIC_ID, "CLINIC_OWNER");
        DoctorProfile profile = doctorProfile(new BigDecimal("25.00"), EmployeeStatus.PASSIVE);
        when(doctorProfileRepository.findById(DOCTOR_PROFILE_ID)).thenReturn(Optional.of(profile));

        BusinessException ex = catchThrowableOfType(() -> service.createDraft(new PayoutDraftRequest(
                DOCTOR_PROFILE_ID, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 5, 1))),
                BusinessException.class);
        assertThat(ex).isNotNull();
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_RULE_VIOLATION);
    }

    // ─── add/remove deduction ───────────────────────────────────────────────

    @Test
    void addDeduction_onDraft_recomputes_andAudits() {
        authenticate(7L, COMPANY_ID, CLINIC_ID, "CLINIC_OWNER");
        DoctorProfile profile = doctorProfile(new BigDecimal("50.00"), EmployeeStatus.ACTIVE);
        PayoutPeriod period = draft(profile, "0.00"); // no existing deduction
        period.setId(500L);

        when(periodRepository.findByIdAndCompanyId(500L, COMPANY_ID)).thenReturn(Optional.of(period));
        when(treatmentRepository.findEligibleForPayout(
                anyLong(), anyLong(), any(TreatmentStatus.class),
                any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(treatment(1L, "2000.00")));
        when(deductionRepository.save(any(PayoutDeduction.class))).thenAnswer(inv -> {
            PayoutDeduction d = inv.getArgument(0);
            d.setId(7000L);
            return d;
        });

        var resp = service.addDeduction(500L, new PayoutDeductionRequest(
                PayoutDeductionType.LAB, "Implant lab", new BigDecimal("250.00")));

        // 2000 × 50% = 1000; deduction 250 → net 750
        assertThat(resp.grossAmount()).isEqualByComparingTo("1000.00");
        assertThat(resp.totalDeduction()).isEqualByComparingTo("250.00");
        assertThat(resp.netAmount()).isEqualByComparingTo("750.00");

        ArgumentCaptor<AuditEvent> ev = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventPublisher).publish(ev.capture());
        assertThat(ev.getValue().type()).isEqualTo(AuditEventType.PAYOUT_DEDUCTION_ADDED);
    }

    @Test
    void addDeduction_onApproved_rejected() {
        authenticate(7L, COMPANY_ID, CLINIC_ID, "CLINIC_OWNER");
        DoctorProfile profile = doctorProfile(new BigDecimal("50.00"), EmployeeStatus.ACTIVE);
        PayoutPeriod period = draft(profile, "0.00");
        period.setStatus(PayoutStatus.APPROVED);
        period.setId(501L);
        when(periodRepository.findByIdAndCompanyId(501L, COMPANY_ID)).thenReturn(Optional.of(period));

        BusinessException ex = catchThrowableOfType(() -> service.addDeduction(501L,
                new PayoutDeductionRequest(PayoutDeductionType.LAB, "x", new BigDecimal("10.00"))),
                BusinessException.class);
        assertThat(ex).isNotNull();
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_RULE_VIOLATION);
        verify(deductionRepository, never()).save(any());
    }

    @Test
    void removeDeduction_missingId_notFound() {
        authenticate(7L, COMPANY_ID, CLINIC_ID, "CLINIC_OWNER");
        DoctorProfile profile = doctorProfile(new BigDecimal("50.00"), EmployeeStatus.ACTIVE);
        PayoutPeriod period = draft(profile, "0.00");
        period.setId(502L);
        when(periodRepository.findByIdAndCompanyId(502L, COMPANY_ID)).thenReturn(Optional.of(period));

        assertThat(catchThrowableOfType(() -> service.removeDeduction(502L, 999L),
                ResourceNotFoundException.class)).isNotNull();
    }

    // ─── approve ────────────────────────────────────────────────────────────

    @Test
    void approve_happyPath_locksTreatments_freezesSnapshots_transitions() {
        authenticate(7L, COMPANY_ID, CLINIC_ID, "CLINIC_OWNER");
        DoctorProfile profile = doctorProfile(new BigDecimal("20.00"), EmployeeStatus.ACTIVE);
        PayoutPeriod period = draft(profile, "0.00");
        period.setId(600L);
        when(periodRepository.findByIdAndCompanyId(600L, COMPANY_ID)).thenReturn(Optional.of(period));

        Treatment t1 = treatment(11L, "1000.00");
        Treatment t2 = treatment(12L, "1500.00");
        when(treatmentRepository.findEligibleForPayout(
                eq(DOCTOR_EMPLOYEE_ID), eq(COMPANY_ID),
                eq(TreatmentStatus.COMPLETED), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(t1, t2));

        var resp = service.approve(600L);

        assertThat(resp.status()).isEqualTo(PayoutStatus.APPROVED);
        // 2500 * 20% = 500
        assertThat(resp.grossAmount()).isEqualByComparingTo("500.00");
        assertThat(resp.commissionRateSnapshot()).isEqualByComparingTo("20.00");
        assertThat(resp.treatmentTotalSnapshot()).isEqualByComparingTo("2500.00");
        assertThat(resp.approvedAt()).isNotNull();

        // Both treatments now point back at the period and have lockedAt set.
        assertThat(t1.isPayoutLocked()).isTrue();
        assertThat(t2.isPayoutLocked()).isTrue();
        assertThat(t1.getPayoutPeriod()).isSameAs(period);
        assertThat(t2.getPayoutPeriod()).isSameAs(period);

        ArgumentCaptor<AuditEvent> ev = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventPublisher).publish(ev.capture());
        assertThat(ev.getValue().type()).isEqualTo(AuditEventType.PAYOUT_APPROVED);
        assertThat(ev.getValue().detail()).containsEntry("lockedTreatmentCount", 2);
    }

    @Test
    void approve_emptyEligibleSet_rejected() {
        authenticate(7L, COMPANY_ID, CLINIC_ID, "CLINIC_OWNER");
        DoctorProfile profile = doctorProfile(new BigDecimal("20.00"), EmployeeStatus.ACTIVE);
        PayoutPeriod period = draft(profile, "0.00");
        period.setId(601L);
        when(periodRepository.findByIdAndCompanyId(601L, COMPANY_ID)).thenReturn(Optional.of(period));
        when(treatmentRepository.findEligibleForPayout(
                anyLong(), anyLong(), any(TreatmentStatus.class),
                any(Instant.class), any(Instant.class)))
                .thenReturn(List.of());

        BusinessException ex = catchThrowableOfType(() -> service.approve(601L), BusinessException.class);
        assertThat(ex).isNotNull();
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_RULE_VIOLATION);
    }

    @Test
    void approve_nonDraft_rejected() {
        authenticate(7L, COMPANY_ID, CLINIC_ID, "CLINIC_OWNER");
        DoctorProfile profile = doctorProfile(new BigDecimal("20.00"), EmployeeStatus.ACTIVE);
        PayoutPeriod period = draft(profile, "0.00");
        period.setStatus(PayoutStatus.APPROVED);
        period.setId(602L);
        when(periodRepository.findByIdAndCompanyId(602L, COMPANY_ID)).thenReturn(Optional.of(period));

        BusinessException ex = catchThrowableOfType(() -> service.approve(602L), BusinessException.class);
        assertThat(ex).isNotNull();
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_RULE_VIOLATION);
    }

    @Test
    void approve_doctorHasNoCommissionRate_rejected() {
        authenticate(7L, COMPANY_ID, CLINIC_ID, "CLINIC_OWNER");
        DoctorProfile profile = doctorProfile(null, EmployeeStatus.ACTIVE);
        PayoutPeriod period = draft(profile, "0.00");
        period.setId(603L);
        when(periodRepository.findByIdAndCompanyId(603L, COMPANY_ID)).thenReturn(Optional.of(period));

        BusinessException ex = catchThrowableOfType(() -> service.approve(603L), BusinessException.class);
        assertThat(ex).isNotNull();
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_RULE_VIOLATION);
    }

    @Test
    void approve_alreadyLockedTreatmentRaceDetected_rejected() {
        authenticate(7L, COMPANY_ID, CLINIC_ID, "CLINIC_OWNER");
        DoctorProfile profile = doctorProfile(new BigDecimal("20.00"), EmployeeStatus.ACTIVE);
        PayoutPeriod period = draft(profile, "0.00");
        period.setId(604L);
        when(periodRepository.findByIdAndCompanyId(604L, COMPANY_ID)).thenReturn(Optional.of(period));

        Treatment locked = treatment(42L, "1000.00");
        locked.setPayoutLockedAt(Instant.now()); // already consumed elsewhere
        when(treatmentRepository.findEligibleForPayout(
                anyLong(), anyLong(), any(TreatmentStatus.class),
                any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(locked));

        BusinessException ex = catchThrowableOfType(() -> service.approve(604L), BusinessException.class);
        assertThat(ex).isNotNull();
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_RULE_VIOLATION);
    }

    // ─── markPaid + cancel ──────────────────────────────────────────────────

    @Test
    void markPaid_onApproved_setsPaidAt_audits() {
        authenticate(7L, COMPANY_ID, CLINIC_ID, "CLINIC_OWNER");
        DoctorProfile profile = doctorProfile(new BigDecimal("20.00"), EmployeeStatus.ACTIVE);
        PayoutPeriod period = draft(profile, "300.00");
        period.setStatus(PayoutStatus.APPROVED);
        period.setId(700L);
        when(periodRepository.findByIdAndCompanyId(700L, COMPANY_ID)).thenReturn(Optional.of(period));
        when(treatmentRepository.findByPayoutPeriodId(700L)).thenReturn(List.of());

        var resp = service.markPaid(700L);

        assertThat(resp.status()).isEqualTo(PayoutStatus.PAID);
        assertThat(resp.paidAt()).isNotNull();
        ArgumentCaptor<AuditEvent> ev = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventPublisher).publish(ev.capture());
        assertThat(ev.getValue().type()).isEqualTo(AuditEventType.PAYOUT_MARKED_PAID);
    }

    @Test
    void markPaid_onDraft_rejected() {
        authenticate(7L, COMPANY_ID, CLINIC_ID, "CLINIC_OWNER");
        DoctorProfile profile = doctorProfile(new BigDecimal("20.00"), EmployeeStatus.ACTIVE);
        PayoutPeriod period = draft(profile, "0.00");
        period.setId(701L);
        when(periodRepository.findByIdAndCompanyId(701L, COMPANY_ID)).thenReturn(Optional.of(period));

        BusinessException ex = catchThrowableOfType(() -> service.markPaid(701L), BusinessException.class);
        assertThat(ex).isNotNull();
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_RULE_VIOLATION);
    }

    @Test
    void cancel_onDraft_transitions() {
        authenticate(7L, COMPANY_ID, CLINIC_ID, "CLINIC_OWNER");
        DoctorProfile profile = doctorProfile(new BigDecimal("20.00"), EmployeeStatus.ACTIVE);
        PayoutPeriod period = draft(profile, "0.00");
        period.setId(800L);
        when(periodRepository.findByIdAndCompanyId(800L, COMPANY_ID)).thenReturn(Optional.of(period));
        when(treatmentRepository.findByPayoutPeriodId(800L)).thenReturn(List.of());

        var resp = service.cancel(800L);
        assertThat(resp.status()).isEqualTo(PayoutStatus.CANCELLED);
    }

    @Test
    void cancel_onApproved_rejected() {
        authenticate(7L, COMPANY_ID, CLINIC_ID, "CLINIC_OWNER");
        DoctorProfile profile = doctorProfile(new BigDecimal("20.00"), EmployeeStatus.ACTIVE);
        PayoutPeriod period = draft(profile, "0.00");
        period.setStatus(PayoutStatus.APPROVED);
        period.setId(801L);
        when(periodRepository.findByIdAndCompanyId(801L, COMPANY_ID)).thenReturn(Optional.of(period));

        BusinessException ex = catchThrowableOfType(() -> service.cancel(801L), BusinessException.class);
        assertThat(ex).isNotNull();
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_RULE_VIOLATION);
    }

    // ─── builders ───────────────────────────────────────────────────────────

    private static Company company() {
        Company c = Company.builder().name("Acme").build();
        c.setId(COMPANY_ID);
        return c;
    }

    private static Clinic clinic(Company c) {
        Clinic cl = Clinic.builder().name("Main").company(c).build();
        cl.setId(CLINIC_ID);
        return cl;
    }

    private static DoctorProfile doctorProfile(BigDecimal rate, EmployeeStatus status) {
        Company c = company();
        Clinic cl = clinic(c);
        Employee e = Employee.builder()
                .firstName("Ayşe").lastName("Demir")
                .email("ayse@acme.com")
                .employeeType(EmployeeType.DOCTOR).status(status)
                .company(c).clinic(cl)
                .build();
        e.setId(DOCTOR_EMPLOYEE_ID);
        DoctorProfile p = DoctorProfile.builder()
                .employee(e)
                .specialty("Ortho")
                .commissionRate(rate)
                .build();
        p.setId(DOCTOR_PROFILE_ID);
        return p;
    }

    private static PayoutPeriod draft(DoctorProfile profile, String initialDeduction) {
        Company c = profile.getEmployee().getCompany();
        Clinic cl = profile.getEmployee().getClinic();
        PayoutPeriod period = PayoutPeriod.builder()
                .company(c).clinic(cl)
                .doctorProfile(profile)
                .periodStart(LocalDate.of(2026, 4, 1))
                .periodEnd(LocalDate.of(2026, 5, 1))
                .status(PayoutStatus.DRAFT)
                .grossAmount(BigDecimal.ZERO)
                .totalDeduction(BigDecimal.ZERO)
                .netAmount(BigDecimal.ZERO)
                .deductions(new ArrayList<>())
                .build();
        BigDecimal ded = new BigDecimal(initialDeduction);
        if (ded.signum() > 0) {
            PayoutDeduction d = PayoutDeduction.builder()
                    .payoutPeriod(period)
                    .type(PayoutDeductionType.OTHER)
                    .amount(ded)
                    .build();
            d.setId(1L);
            period.getDeductions().add(d);
        }
        return period;
    }

    private static Treatment treatment(Long id, String fee) {
        Company c = company();
        Clinic cl = clinic(c);
        Patient p = Patient.builder()
                .firstName("Ali").lastName("Veli")
                .phone("+9050000000")
                .company(c).clinic(cl)
                .build();
        p.setId(800L + id);
        Employee d = Employee.builder()
                .firstName("Ayşe").lastName("Demir")
                .employeeType(EmployeeType.DOCTOR).status(EmployeeStatus.ACTIVE)
                .company(c).clinic(cl)
                .build();
        d.setId(DOCTOR_EMPLOYEE_ID);
        Treatment t = Treatment.builder()
                .company(c).clinic(cl).patient(p).doctor(d)
                .name("Dolgu")
                .performedAt(Instant.parse("2026-04-15T10:00:00Z"))
                .completedAt(Instant.parse("2026-04-15T11:00:00Z"))
                .fee(new BigDecimal(fee))
                .currency("TRY")
                .status(TreatmentStatus.COMPLETED)
                .build();
        t.setId(id);
        return t;
    }

    private static void authenticate(Long userId, Long companyId, Long clinicId, String role) {
        AuthenticatedUser principal = new AuthenticatedUser(
                userId, "user@example.com", companyId, clinicId, Set.of(role), List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.authorities()));
    }
}
