package com.wedent.clinic.payout.service.impl;

import com.wedent.clinic.common.audit.AuditEventPublisher;
import com.wedent.clinic.common.audit.event.AuditEvent;
import com.wedent.clinic.common.audit.event.AuditEventType;
import com.wedent.clinic.common.dto.PageResponse;
import com.wedent.clinic.common.exception.BusinessException;
import com.wedent.clinic.common.exception.ErrorCode;
import com.wedent.clinic.common.exception.ResourceNotFoundException;
import com.wedent.clinic.employee.entity.DoctorProfile;
import com.wedent.clinic.employee.entity.Employee;
import com.wedent.clinic.employee.entity.EmployeeStatus;
import com.wedent.clinic.employee.repository.DoctorProfileRepository;
import com.wedent.clinic.payout.dto.PayoutDeductionRequest;
import com.wedent.clinic.payout.dto.PayoutDraftRequest;
import com.wedent.clinic.payout.dto.PayoutResponse;
import com.wedent.clinic.payout.dto.PayoutTreatmentLine;
import com.wedent.clinic.payout.entity.PayoutDeduction;
import com.wedent.clinic.payout.entity.PayoutPeriod;
import com.wedent.clinic.payout.entity.PayoutStatus;
import com.wedent.clinic.payout.mapper.PayoutMapper;
import com.wedent.clinic.payout.repository.PayoutDeductionRepository;
import com.wedent.clinic.payout.repository.PayoutPeriodRepository;
import com.wedent.clinic.payout.service.PayoutCalculator;
import com.wedent.clinic.payout.service.PayoutService;
import com.wedent.clinic.security.SecurityUtils;
import com.wedent.clinic.treatment.entity.Treatment;
import com.wedent.clinic.treatment.entity.TreatmentStatus;
import com.wedent.clinic.treatment.repository.TreatmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Hakediş orchestration. The calculator stays pure; this class owns
 * everything stateful — tenant scope, lifecycle guards, treatment
 * locking, audit trails.
 *
 * <p><b>Approve is the critical path:</b> it runs inside a single
 * {@code @Transactional} boundary that (a) re-queries eligible
 * treatments and fails if anything now looks stale, (b) stamps
 * {@code payoutLockedAt} + {@code payoutPeriod} on each one, and
 * (c) snapshots {@code commissionRate} + {@code treatmentTotal} onto
 * the period row. After approve nothing about the issued payout
 * depends on live {@code DoctorProfile} / {@code Treatment} data.</p>
 *
 * <p>Role gating is method-level on the controller; the service layer
 * enforces tenant + clinic isolation as defense-in-depth so a future
 * internal caller can't bypass the {@code @PreAuthorize}.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PayoutServiceImpl implements PayoutService {

    private final PayoutPeriodRepository periodRepository;
    private final PayoutDeductionRepository deductionRepository;
    private final DoctorProfileRepository doctorProfileRepository;
    private final TreatmentRepository treatmentRepository;
    private final PayoutCalculator calculator;
    private final PayoutMapper mapper;
    private final AuditEventPublisher auditEventPublisher;

    // ─── Lifecycle ─────────────────────────────────────────────────────────

    @Override
    public PayoutResponse createDraft(PayoutDraftRequest request) {
        validateWindow(request.periodStart(), request.periodEnd());

        Long companyId = SecurityUtils.currentCompanyId();
        DoctorProfile profile = loadDoctorProfileInScope(request.doctorProfileId(), companyId);
        Employee employee = profile.getEmployee();
        SecurityUtils.verifyClinicAccess(employee.getClinic().getId());

        Instant from = toInstant(request.periodStart());
        Instant to   = toInstant(request.periodEnd());

        List<Treatment> eligible = treatmentRepository.findEligibleForPayout(
                employee.getId(), companyId, TreatmentStatus.COMPLETED, from, to);

        BigDecimal rate = profile.getCommissionRate();
        PayoutCalculator.Result calc = calculator.compute(eligible, rate, BigDecimal.ZERO);

        PayoutPeriod period = PayoutPeriod.builder()
                .company(employee.getCompany())
                .clinic(employee.getClinic())
                .doctorProfile(profile)
                .periodStart(request.periodStart())
                .periodEnd(request.periodEnd())
                .status(PayoutStatus.DRAFT)
                .grossAmount(calc.grossAmount())
                .totalDeduction(calc.totalDeduction())
                .netAmount(calc.netAmount())
                .build();
        PayoutPeriod saved = periodRepository.save(period);

        auditEventPublisher.publish(AuditEvent.builder(AuditEventType.PAYOUT_DRAFT_CREATED)
                .actorUserId(SecurityUtils.currentUserIdOrNull())
                .companyId(companyId)
                .clinicId(saved.getClinic().getId())
                .targetType("PayoutPeriod")
                .targetId(saved.getId())
                .detail(Map.of(
                        "doctorProfileId", profile.getId(),
                        "periodStart", request.periodStart().toString(),
                        "periodEnd", request.periodEnd().toString(),
                        "eligibleTreatmentCount", eligible.size(),
                        "grossAmount", calc.grossAmount()))
                .build());

        return mapper.toResponse(saved, eligible.stream().map(mapper::toTreatmentLine).toList());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PayoutResponse> list(Long doctorProfileId,
                                             PayoutStatus status,
                                             LocalDate from,
                                             LocalDate to,
                                             Pageable pageable) {
        Long companyId = SecurityUtils.currentCompanyId();
        Long clinicScope = resolveClinicScope();

        Page<PayoutPeriod> page = periodRepository.search(
                companyId, clinicScope, doctorProfileId, status, from, to, pageable);
        return PageResponse.of(page.map(mapper::toSummary));
    }

    @Override
    @Transactional(readOnly = true)
    public PayoutResponse getById(Long id) {
        PayoutPeriod period = loadInScope(id);
        verifyClinicReadAccess(period);
        return mapper.toResponse(period, loadIncludedTreatmentLines(period));
    }

    @Override
    public PayoutResponse addDeduction(Long id, PayoutDeductionRequest request) {
        PayoutPeriod period = loadInScope(id);
        SecurityUtils.verifyClinicAccess(period.getClinic().getId());
        assertDraft(period);

        PayoutDeduction deduction = PayoutDeduction.builder()
                .payoutPeriod(period)
                .type(request.type())
                .description(request.description())
                .amount(request.amount())
                .build();
        period.getDeductions().add(deduction);
        deductionRepository.save(deduction);

        recomputeLive(period);

        auditEventPublisher.publish(AuditEvent.builder(AuditEventType.PAYOUT_DEDUCTION_ADDED)
                .actorUserId(SecurityUtils.currentUserIdOrNull())
                .companyId(period.getCompany().getId())
                .clinicId(period.getClinic().getId())
                .targetType("PayoutPeriod")
                .targetId(period.getId())
                .detail(Map.of(
                        "deductionId", deduction.getId(),
                        "type", deduction.getType().name(),
                        "amount", deduction.getAmount()))
                .build());

        return mapper.toResponse(period, loadIncludedTreatmentLines(period));
    }

    @Override
    public PayoutResponse removeDeduction(Long id, Long deductionId) {
        PayoutPeriod period = loadInScope(id);
        SecurityUtils.verifyClinicAccess(period.getClinic().getId());
        assertDraft(period);

        PayoutDeduction target = period.getDeductions().stream()
                .filter(d -> d.getId().equals(deductionId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("PayoutDeduction", deductionId));

        period.getDeductions().remove(target); // orphanRemoval deletes row
        recomputeLive(period);

        auditEventPublisher.publish(AuditEvent.builder(AuditEventType.PAYOUT_DEDUCTION_REMOVED)
                .actorUserId(SecurityUtils.currentUserIdOrNull())
                .companyId(period.getCompany().getId())
                .clinicId(period.getClinic().getId())
                .targetType("PayoutPeriod")
                .targetId(period.getId())
                .detail(Map.of(
                        "deductionId", deductionId,
                        "type", target.getType().name(),
                        "amount", target.getAmount()))
                .build());

        return mapper.toResponse(period, loadIncludedTreatmentLines(period));
    }

    @Override
    public PayoutResponse recalculate(Long id) {
        PayoutPeriod period = loadInScope(id);
        SecurityUtils.verifyClinicAccess(period.getClinic().getId());
        assertDraft(period);

        BigDecimal grossBefore = period.getGrossAmount();
        BigDecimal netBefore = period.getNetAmount();
        recomputeLive(period);

        Map<String, Object> detail = new HashMap<>();
        detail.put("grossBefore", grossBefore);
        detail.put("grossAfter", period.getGrossAmount());
        detail.put("netBefore", netBefore);
        detail.put("netAfter", period.getNetAmount());

        auditEventPublisher.publish(AuditEvent.builder(AuditEventType.PAYOUT_RECALCULATED)
                .actorUserId(SecurityUtils.currentUserIdOrNull())
                .companyId(period.getCompany().getId())
                .clinicId(period.getClinic().getId())
                .targetType("PayoutPeriod")
                .targetId(period.getId())
                .detail(detail)
                .build());

        return mapper.toResponse(period, loadIncludedTreatmentLines(period));
    }

    @Override
    public PayoutResponse approve(Long id) {
        PayoutPeriod period = loadInScope(id);
        SecurityUtils.verifyClinicAccess(period.getClinic().getId());
        assertDraft(period);

        DoctorProfile profile = period.getDoctorProfile();
        if (profile.getCommissionRate() == null) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Doctor has no commission rate configured — cannot approve payout");
        }

        Instant from = toInstant(period.getPeriodStart());
        Instant to   = toInstant(period.getPeriodEnd());
        Employee employee = profile.getEmployee();

        // Re-query live: any treatment locked since DRAFT creation must be
        // excluded, and any newly-completed one is now picked up.
        List<Treatment> eligible = treatmentRepository.findEligibleForPayout(
                employee.getId(), period.getCompany().getId(),
                TreatmentStatus.COMPLETED, from, to);

        if (eligible.isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Cannot approve an empty payout");
        }

        BigDecimal totalDeduction = sumDeductions(period);
        PayoutCalculator.Result calc = calculator.compute(
                eligible, profile.getCommissionRate(), totalDeduction);

        // Freeze snapshots + transition status
        Instant now = Instant.now();
        period.setTreatmentTotalSnapshot(calc.treatmentTotal());
        period.setCommissionRateSnapshot(profile.getCommissionRate());
        period.setGrossAmount(calc.grossAmount());
        period.setTotalDeduction(calc.totalDeduction());
        period.setNetAmount(calc.netAmount());
        period.setStatus(PayoutStatus.APPROVED);
        period.setApprovedAt(now);

        // Lock treatments — same transaction. Defensive double-check.
        for (Treatment t : eligible) {
            if (t.isPayoutLocked()) {
                throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                        "Treatment " + t.getId() + " was locked by another payout mid-approve");
            }
            t.setPayoutLockedAt(now);
            t.setPayoutPeriod(period);
        }

        auditEventPublisher.publish(AuditEvent.builder(AuditEventType.PAYOUT_APPROVED)
                .actorUserId(SecurityUtils.currentUserIdOrNull())
                .companyId(period.getCompany().getId())
                .clinicId(period.getClinic().getId())
                .targetType("PayoutPeriod")
                .targetId(period.getId())
                .detail(Map.of(
                        "doctorProfileId", profile.getId(),
                        "lockedTreatmentCount", eligible.size(),
                        "grossAmount", calc.grossAmount(),
                        "netAmount", calc.netAmount(),
                        "commissionRateSnapshot", profile.getCommissionRate()))
                .build());

        return mapper.toResponse(period, eligible.stream().map(mapper::toTreatmentLine).toList());
    }

    @Override
    public PayoutResponse markPaid(Long id) {
        PayoutPeriod period = loadInScope(id);
        SecurityUtils.verifyClinicAccess(period.getClinic().getId());

        if (period.getStatus() != PayoutStatus.APPROVED) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Only APPROVED payouts can be marked PAID (current: " + period.getStatus() + ")");
        }

        period.setStatus(PayoutStatus.PAID);
        period.setPaidAt(Instant.now());

        auditEventPublisher.publish(AuditEvent.builder(AuditEventType.PAYOUT_MARKED_PAID)
                .actorUserId(SecurityUtils.currentUserIdOrNull())
                .companyId(period.getCompany().getId())
                .clinicId(period.getClinic().getId())
                .targetType("PayoutPeriod")
                .targetId(period.getId())
                .detail(Map.of(
                        "netAmount", period.getNetAmount(),
                        "paidAt", period.getPaidAt().toString()))
                .build());

        return mapper.toResponse(period, loadIncludedTreatmentLines(period));
    }

    @Override
    public PayoutResponse cancel(Long id) {
        PayoutPeriod period = loadInScope(id);
        SecurityUtils.verifyClinicAccess(period.getClinic().getId());

        if (period.getStatus() != PayoutStatus.DRAFT) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Only DRAFT payouts can be cancelled (current: " + period.getStatus() + ")");
        }

        period.setStatus(PayoutStatus.CANCELLED);

        auditEventPublisher.publish(AuditEvent.builder(AuditEventType.PAYOUT_CANCELLED)
                .actorUserId(SecurityUtils.currentUserIdOrNull())
                .companyId(period.getCompany().getId())
                .clinicId(period.getClinic().getId())
                .targetType("PayoutPeriod")
                .targetId(period.getId())
                .build());

        return mapper.toResponse(period, loadIncludedTreatmentLines(period));
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    private DoctorProfile loadDoctorProfileInScope(Long profileId, Long companyId) {
        DoctorProfile profile = doctorProfileRepository.findById(profileId)
                .orElseThrow(() -> new ResourceNotFoundException("DoctorProfile", profileId));
        Employee employee = profile.getEmployee();
        if (employee == null
                || employee.getCompany() == null
                || !companyId.equals(employee.getCompany().getId())) {
            // Don't leak existence across tenants.
            throw new ResourceNotFoundException("DoctorProfile", profileId);
        }
        if (employee.getStatus() != EmployeeStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Doctor is not active");
        }
        return profile;
    }

    private PayoutPeriod loadInScope(Long id) {
        Long companyId = SecurityUtils.currentCompanyId();
        return periodRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("PayoutPeriod", id));
    }

    private void assertDraft(PayoutPeriod period) {
        if (period.getStatus() != PayoutStatus.DRAFT) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Payout is not editable (status=" + period.getStatus() + ")");
        }
    }

    private void validateWindow(LocalDate from, LocalDate to) {
        if (from == null || to == null || !from.isBefore(to)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "periodStart must be before periodEnd");
        }
    }

    private static Instant toInstant(LocalDate date) {
        return date.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    /**
     * Recompute gross/total/net from live eligible treatments + current
     * deductions. Does not touch snapshots — those are only written at
     * APPROVE time.
     */
    private void recomputeLive(PayoutPeriod period) {
        DoctorProfile profile = period.getDoctorProfile();
        Employee employee = profile.getEmployee();
        Instant from = toInstant(period.getPeriodStart());
        Instant to   = toInstant(period.getPeriodEnd());
        List<Treatment> eligible = treatmentRepository.findEligibleForPayout(
                employee.getId(), period.getCompany().getId(),
                TreatmentStatus.COMPLETED, from, to);
        BigDecimal totalDeduction = sumDeductions(period);
        PayoutCalculator.Result calc = calculator.compute(
                eligible, profile.getCommissionRate(), totalDeduction);
        period.setGrossAmount(calc.grossAmount());
        period.setTotalDeduction(calc.totalDeduction());
        period.setNetAmount(calc.netAmount());
    }

    private static BigDecimal sumDeductions(PayoutPeriod period) {
        if (period.getDeductions() == null) return BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        for (PayoutDeduction d : period.getDeductions()) {
            if (d.getAmount() != null) total = total.add(d.getAmount());
        }
        return total;
    }

    /**
     * Detail-endpoint helper — for APPROVED/PAID periods we surface the
     * actually-locked treatments (stable, historical view); for DRAFTs
     * we show the currently-eligible set so the FE can show what the
     * approve button will snapshot.
     */
    private List<PayoutTreatmentLine> loadIncludedTreatmentLines(PayoutPeriod period) {
        if (period.isDraft()) {
            DoctorProfile profile = period.getDoctorProfile();
            Employee employee = profile.getEmployee();
            Instant from = toInstant(period.getPeriodStart());
            Instant to   = toInstant(period.getPeriodEnd());
            List<Treatment> eligible = treatmentRepository.findEligibleForPayout(
                    employee.getId(), period.getCompany().getId(),
                    TreatmentStatus.COMPLETED, from, to);
            return eligible.stream().map(mapper::toTreatmentLine).toList();
        }
        // APPROVED/PAID/CANCELLED — treatments that point back at this period.
        return treatmentRepository.findByPayoutPeriodId(period.getId())
                .stream()
                .map(mapper::toTreatmentLine)
                .toList();
    }

    /**
     * List-filter helper: non-owner users are pinned to their own clinic
     * regardless of the query parameter they try to slip in.
     */
    private static Long resolveClinicScope() {
        if (SecurityUtils.hasRole(SecurityUtils.ROLE_CLINIC_OWNER)) {
            return null;
        }
        return SecurityUtils.currentClinicId().orElse(null);
    }

    private static void verifyClinicReadAccess(PayoutPeriod period) {
        SecurityUtils.verifyClinicAccess(period.getClinic().getId());
    }
}
