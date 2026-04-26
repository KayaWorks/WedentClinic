package com.wedent.clinic.payment;

import com.wedent.clinic.clinic.entity.Clinic;
import com.wedent.clinic.common.audit.AuditEventPublisher;
import com.wedent.clinic.common.audit.event.AuditEvent;
import com.wedent.clinic.common.audit.event.AuditEventType;
import com.wedent.clinic.common.exception.BusinessException;
import com.wedent.clinic.common.exception.ErrorCode;
import com.wedent.clinic.common.exception.ResourceNotFoundException;
import com.wedent.clinic.company.entity.Company;
import com.wedent.clinic.patient.entity.Patient;
import com.wedent.clinic.patient.repository.PatientRepository;
import com.wedent.clinic.payment.dto.PatientBalanceResponse;
import com.wedent.clinic.payment.dto.PaymentCancelRequest;
import com.wedent.clinic.payment.dto.PaymentCreateRequest;
import com.wedent.clinic.payment.dto.PaymentResponse;
import com.wedent.clinic.payment.entity.Payment;
import com.wedent.clinic.payment.entity.PaymentMethod;
import com.wedent.clinic.payment.entity.PaymentStatus;
import com.wedent.clinic.payment.mapper.PaymentMapper;
import com.wedent.clinic.payment.repository.PaymentRepository;
import com.wedent.clinic.payment.service.impl.PaymentServiceImpl;
import com.wedent.clinic.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentServiceImplTest {

    private static final Long COMPANY_ID  = 100L;
    private static final Long CLINIC_ID   = 10L;
    private static final Long PATIENT_ID  = 200L;
    private static final Long PAYMENT_ID  = 400L;

    private final PaymentRepository  paymentRepository  = Mockito.mock(PaymentRepository.class);
    private final PatientRepository  patientRepository  = Mockito.mock(PatientRepository.class);
    private final PaymentMapper      mapper             = Mockito.mock(PaymentMapper.class);
    private final AuditEventPublisher auditPublisher    = Mockito.mock(AuditEventPublisher.class);

    private final PaymentServiceImpl service = new PaymentServiceImpl(
            paymentRepository, patientRepository, mapper, auditPublisher);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    // ─── create ─────────────────────────────────────────────────────────────

    @Test
    void create_happyPath_wiresRelations_appliesDefaults_andAudits() {
        authenticate(7L, COMPANY_ID, CLINIC_ID, "MANAGER");
        Patient patient = patient();
        when(patientRepository.findByIdAndCompanyId(PATIENT_ID, COMPANY_ID)).thenReturn(Optional.of(patient));

        Payment partial = new Payment();
        partial.setAmount(new BigDecimal("500.00"));
        when(mapper.toEntity(any(PaymentCreateRequest.class))).thenReturn(partial);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(PAYMENT_ID);
            return p;
        });
        when(mapper.toResponse(any(Payment.class))).thenReturn(stubResponse(PAYMENT_ID));

        PaymentResponse response = service.create(PATIENT_ID,
                new PaymentCreateRequest(new BigDecimal("500.00"), null, null, null, "Test ödeme"));

        assertThat(response.id()).isEqualTo(PAYMENT_ID);

        ArgumentCaptor<Payment> savedCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(savedCaptor.capture());
        Payment saved = savedCaptor.getValue();
        assertThat(saved.getPatient()).isSameAs(patient);
        assertThat(saved.getCompany()).isSameAs(patient.getCompany());
        assertThat(saved.getClinic()).isSameAs(patient.getClinic());
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(saved.getCurrency()).isEqualTo("TRY");
        assertThat(saved.getMethod()).isEqualTo(PaymentMethod.CASH);
        assertThat(saved.getPaidAt()).isNotNull();

        ArgumentCaptor<AuditEvent> evCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditPublisher).publish(evCaptor.capture());
        assertThat(evCaptor.getValue().type()).isEqualTo(AuditEventType.PAYMENT_CREATED);
        assertThat(evCaptor.getValue().detail()).containsEntry("patientId", PATIENT_ID);
    }

    @Test
    void create_patientNotFound_throwsResourceNotFound() {
        authenticate(7L, COMPANY_ID, CLINIC_ID, "MANAGER");
        when(patientRepository.findByIdAndCompanyId(PATIENT_ID, COMPANY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(PATIENT_ID,
                new PaymentCreateRequest(new BigDecimal("100.00"), null, null, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(paymentRepository, never()).save(any());
        verify(auditPublisher, never()).publish(any());
    }

    // ─── cancel ─────────────────────────────────────────────────────────────

    @Test
    void cancel_happyPath_setsStatusAndAudits() {
        authenticate(7L, COMPANY_ID, CLINIC_ID, "MANAGER");
        Payment existing = existingPayment(PaymentStatus.COMPLETED);
        when(paymentRepository.findByIdAndCompanyId(PAYMENT_ID, COMPANY_ID)).thenReturn(Optional.of(existing));
        when(mapper.toResponse(any(Payment.class))).thenReturn(stubResponse(PAYMENT_ID));

        service.cancel(PAYMENT_ID, new PaymentCancelRequest("Yanlış giriş"));

        assertThat(existing.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(existing.getCancelledAt()).isNotNull();
        assertThat(existing.getCancelReason()).isEqualTo("Yanlış giriş");

        ArgumentCaptor<AuditEvent> evCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditPublisher).publish(evCaptor.capture());
        assertThat(evCaptor.getValue().type()).isEqualTo(AuditEventType.PAYMENT_CANCELLED);
    }

    @Test
    void cancel_alreadyCancelled_throwsBusinessRuleViolation() {
        authenticate(7L, COMPANY_ID, CLINIC_ID, "MANAGER");
        Payment existing = existingPayment(PaymentStatus.CANCELLED);
        when(paymentRepository.findByIdAndCompanyId(PAYMENT_ID, COMPANY_ID)).thenReturn(Optional.of(existing));

        BusinessException ex = catchThrowableOfType(
                () -> service.cancel(PAYMENT_ID, null),
                BusinessException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_RULE_VIOLATION);
        verify(auditPublisher, never()).publish(any());
    }

    @Test
    void cancel_paymentNotFound_throwsResourceNotFound() {
        authenticate(7L, COMPANY_ID, CLINIC_ID, "MANAGER");
        when(paymentRepository.findByIdAndCompanyId(PAYMENT_ID, COMPANY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancel(PAYMENT_ID, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ─── balance ────────────────────────────────────────────────────────────

    @Test
    void getBalance_returnsCorrectBalanceFigures() {
        authenticate(7L, COMPANY_ID, CLINIC_ID, "MANAGER");
        when(patientRepository.findByIdAndCompanyId(PATIENT_ID, COMPANY_ID)).thenReturn(Optional.of(patient()));
        when(paymentRepository.sumByPatientIdAndCompanyIdAndStatus(PATIENT_ID, COMPANY_ID, PaymentStatus.COMPLETED))
                .thenReturn(new BigDecimal("1500.00"));
        when(paymentRepository.sumTreatmentFeeByPatientIdAndCompanyId(PATIENT_ID, COMPANY_ID))
                .thenReturn(new BigDecimal("1200.00"));

        PatientBalanceResponse balance = service.getBalance(PATIENT_ID);

        assertThat(balance.patientId()).isEqualTo(PATIENT_ID);
        assertThat(balance.totalPaid()).isEqualByComparingTo("1500.00");
        assertThat(balance.totalFees()).isEqualByComparingTo("1200.00");
        assertThat(balance.balance()).isEqualByComparingTo("300.00");
    }

    @Test
    void getBalance_noPaymentsOrTreatments_returnsZeroBalance() {
        authenticate(7L, COMPANY_ID, CLINIC_ID, "MANAGER");
        when(patientRepository.findByIdAndCompanyId(PATIENT_ID, COMPANY_ID)).thenReturn(Optional.of(patient()));
        when(paymentRepository.sumByPatientIdAndCompanyIdAndStatus(PATIENT_ID, COMPANY_ID, PaymentStatus.COMPLETED))
                .thenReturn(null);
        when(paymentRepository.sumTreatmentFeeByPatientIdAndCompanyId(PATIENT_ID, COMPANY_ID))
                .thenReturn(null);

        PatientBalanceResponse balance = service.getBalance(PATIENT_ID);

        assertThat(balance.totalPaid()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(balance.totalFees()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(balance.balance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void getBalance_negativeBalance_patientOwesClinic() {
        authenticate(7L, COMPANY_ID, CLINIC_ID, "MANAGER");
        when(patientRepository.findByIdAndCompanyId(PATIENT_ID, COMPANY_ID)).thenReturn(Optional.of(patient()));
        when(paymentRepository.sumByPatientIdAndCompanyIdAndStatus(PATIENT_ID, COMPANY_ID, PaymentStatus.COMPLETED))
                .thenReturn(new BigDecimal("200.00"));
        when(paymentRepository.sumTreatmentFeeByPatientIdAndCompanyId(PATIENT_ID, COMPANY_ID))
                .thenReturn(new BigDecimal("800.00"));

        PatientBalanceResponse balance = service.getBalance(PATIENT_ID);

        assertThat(balance.balance()).isEqualByComparingTo("-600.00");
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

    private static Payment existingPayment(PaymentStatus status) {
        Patient p = patient();
        Payment pay = Payment.builder()
                .patient(p)
                .company(p.getCompany())
                .clinic(p.getClinic())
                .amount(new BigDecimal("500.00"))
                .currency("TRY")
                .method(PaymentMethod.CASH)
                .status(status)
                .paidAt(Instant.now())
                .build();
        pay.setId(PAYMENT_ID);
        return pay;
    }

    private static PaymentResponse stubResponse(Long id) {
        return new PaymentResponse(
                id, PATIENT_ID, CLINIC_ID, COMPANY_ID,
                new BigDecimal("500.00"), "TRY",
                PaymentMethod.CASH, PaymentStatus.COMPLETED,
                Instant.now(), null, null, null, null, null);
    }

    private static void authenticate(Long userId, Long companyId, Long clinicId, String role) {
        AuthenticatedUser principal = new AuthenticatedUser(
                userId, "user@example.com", companyId, clinicId, Set.of(role), List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.authorities()));
    }
}
