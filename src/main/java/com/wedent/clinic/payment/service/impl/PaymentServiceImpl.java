package com.wedent.clinic.payment.service.impl;

import com.wedent.clinic.common.audit.AuditEventPublisher;
import com.wedent.clinic.common.audit.event.AuditEvent;
import com.wedent.clinic.common.audit.event.AuditEventType;
import com.wedent.clinic.common.dto.PageResponse;
import com.wedent.clinic.common.exception.BusinessException;
import com.wedent.clinic.common.exception.ErrorCode;
import com.wedent.clinic.common.exception.ResourceNotFoundException;
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
import com.wedent.clinic.payment.service.PaymentService;
import com.wedent.clinic.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Payment lifecycle. Payments are create-and-cancel only — amounts are never
 * edited in place so the audit trail stays clean. A wrong entry is cancelled
 * and a corrected one is recorded instead.
 *
 * <p>Balance = totalPaid (COMPLETED) − totalFees (all active treatments).
 * Both figures are computed in a single read-only transaction from the DB
 * so they're always consistent with live treatment/payment data.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PaymentServiceImpl implements PaymentService {

    private static final String DEFAULT_CURRENCY = "TRY";

    private final PaymentRepository repository;
    private final PatientRepository patientRepository;
    @Qualifier("paymentMapperImpl")
    private final PaymentMapper mapper;
    private final AuditEventPublisher auditEventPublisher;

    @Override
    public PaymentResponse create(Long patientId, PaymentCreateRequest request) {
        Long companyId = SecurityUtils.currentCompanyId();
        Patient patient = loadPatient(patientId, companyId);
        SecurityUtils.verifyClinicAccess(patient.getClinic().getId());

        Payment entity = mapper.toEntity(request);
        entity.setPatient(patient);
        entity.setCompany(patient.getCompany());
        entity.setClinic(patient.getClinic());
        entity.setStatus(PaymentStatus.COMPLETED);
        if (entity.getCurrency() == null || entity.getCurrency().isBlank()) {
            entity.setCurrency(DEFAULT_CURRENCY);
        }
        if (entity.getMethod() == null) {
            entity.setMethod(PaymentMethod.CASH);
        }
        if (entity.getPaidAt() == null) {
            entity.setPaidAt(Instant.now());
        }

        Payment saved = repository.save(entity);

        auditEventPublisher.publish(AuditEvent.builder(AuditEventType.PAYMENT_CREATED)
                .actorUserId(SecurityUtils.currentUserIdOrNull())
                .companyId(companyId)
                .clinicId(patient.getClinic().getId())
                .targetType("Payment")
                .targetId(saved.getId())
                .detail(Map.of(
                        "patientId", patientId,
                        "amount", saved.getAmount(),
                        "method", saved.getMethod().name()))
                .build());

        return mapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PaymentResponse> listForPatient(Long patientId, Pageable pageable) {
        Long companyId = SecurityUtils.currentCompanyId();
        Patient patient = loadPatient(patientId, companyId);
        SecurityUtils.verifyClinicAccess(patient.getClinic().getId());

        Page<Payment> page = repository.findByPatientIdAndCompanyId(patientId, companyId, pageable);
        return PageResponse.of(page.map(mapper::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getById(Long paymentId) {
        Long companyId = SecurityUtils.currentCompanyId();
        Payment payment = loadPaymentInScope(paymentId, companyId);
        SecurityUtils.verifyClinicAccess(payment.getClinic().getId());
        return mapper.toResponse(payment);
    }

    @Override
    public PaymentResponse cancel(Long paymentId, PaymentCancelRequest request) {
        Long companyId = SecurityUtils.currentCompanyId();
        Payment payment = loadPaymentInScope(paymentId, companyId);
        SecurityUtils.verifyClinicAccess(payment.getClinic().getId());

        if (payment.getStatus() == PaymentStatus.CANCELLED) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Payment is already cancelled");
        }

        payment.setStatus(PaymentStatus.CANCELLED);
        payment.setCancelledAt(Instant.now());
        if (request != null && request.cancelReason() != null) {
            payment.setCancelReason(request.cancelReason());
        }

        auditEventPublisher.publish(AuditEvent.builder(AuditEventType.PAYMENT_CANCELLED)
                .actorUserId(SecurityUtils.currentUserIdOrNull())
                .companyId(companyId)
                .clinicId(payment.getClinic().getId())
                .targetType("Payment")
                .targetId(payment.getId())
                .detail(Map.of(
                        "patientId", payment.getPatient().getId(),
                        "amount", payment.getAmount(),
                        "cancelReason", request != null && request.cancelReason() != null
                                ? request.cancelReason() : ""))
                .build());

        return mapper.toResponse(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public PatientBalanceResponse getBalance(Long patientId) {
        Long companyId = SecurityUtils.currentCompanyId();
        Patient patient = loadPatient(patientId, companyId);
        SecurityUtils.verifyClinicAccess(patient.getClinic().getId());

        BigDecimal totalPaid = repository
                .sumByPatientIdAndCompanyIdAndStatus(patientId, companyId, PaymentStatus.COMPLETED);
        if (totalPaid == null) totalPaid = BigDecimal.ZERO;

        BigDecimal totalFees = repository
                .sumTreatmentFeeByPatientIdAndCompanyId(patientId, companyId);
        if (totalFees == null) totalFees = BigDecimal.ZERO;

        BigDecimal balance = totalPaid.subtract(totalFees);

        return new PatientBalanceResponse(patientId, totalPaid, totalFees, balance, DEFAULT_CURRENCY);
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    private Patient loadPatient(Long patientId, Long companyId) {
        return patientRepository.findByIdAndCompanyId(patientId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", patientId));
    }

    private Payment loadPaymentInScope(Long paymentId, Long companyId) {
        return repository.findByIdAndCompanyId(paymentId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));
    }
}
