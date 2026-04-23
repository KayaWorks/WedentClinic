package com.wedent.clinic.dashboard.service.impl;

import com.wedent.clinic.appointment.entity.AppointmentStatus;
import com.wedent.clinic.appointment.repository.AppointmentRepository;
import com.wedent.clinic.common.audit.entity.AuditLog;
import com.wedent.clinic.common.audit.event.AuditEventType;
import com.wedent.clinic.common.audit.repository.AuditLogRepository;
import com.wedent.clinic.common.tenant.TenantScopeResolver;
import com.wedent.clinic.dashboard.dto.DashboardSummaryResponse;
import com.wedent.clinic.dashboard.dto.DashboardSummaryResponse.DailyBucket;
import com.wedent.clinic.dashboard.dto.DashboardSummaryResponse.RecentActivity;
import com.wedent.clinic.dashboard.dto.DashboardSummaryResponse.Scope;
import com.wedent.clinic.dashboard.dto.DashboardSummaryResponse.TodayBreakdown;
import com.wedent.clinic.dashboard.dto.DashboardSummaryResponse.Totals;
import com.wedent.clinic.dashboard.service.DashboardService;
import com.wedent.clinic.employee.entity.EmployeeStatus;
import com.wedent.clinic.employee.entity.EmployeeType;
import com.wedent.clinic.employee.repository.EmployeeRepository;
import com.wedent.clinic.patient.repository.PatientRepository;
import com.wedent.clinic.security.AuthenticatedUser;
import com.wedent.clinic.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Dashboard assembly — one read-only transaction issues ~6 small queries and
 * stitches the payload. Intentionally kept as one coarse-grained method so
 * the frontend can fetch the landing page with a single round-trip.
 *
 * <p>Tenant scope: owners see company-wide counts (clinicId == null);
 * everyone else is clamped to their own clinic via
 * {@link TenantScopeResolver}. Passing {@code null} as the requested clinic
 * therefore collapses to "the caller's clinic" for non-owners — which is
 * exactly what we want for a shell landing page.</p>
 */
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private static final int NEXT_DAYS = 7;
    private static final int RECENT_ACTIVITY_LIMIT = 10;

    /**
     * Whitelist of audit event types that are safe to surface on the
     * dashboard. Anything touching credentials (LOGIN_FAILURE, refresh replay,
     * password change) stays out — those belong in the security feed, not
     * the day-to-day activity feed.
     */
    private static final Set<String> DASHBOARD_EVENT_TYPES = Set.of(
            AuditEventType.APPOINTMENT_CREATED.name(),
            AuditEventType.APPOINTMENT_UPDATED.name(),
            AuditEventType.APPOINTMENT_STATUS_CHANGED.name(),
            AuditEventType.APPOINTMENT_DELETED.name(),
            AuditEventType.USER_CREATED.name(),
            AuditEventType.USER_ROLE_CHANGED.name()
    );

    private final PatientRepository patientRepository;
    private final EmployeeRepository employeeRepository;
    private final AppointmentRepository appointmentRepository;
    private final AuditLogRepository auditLogRepository;
    private final TenantScopeResolver tenantScopeResolver;

    @Override
    @Transactional(readOnly = true)
    public DashboardSummaryResponse summary() {
        AuthenticatedUser caller = SecurityUtils.currentUser();
        Long companyId = caller.companyId();
        // For owners the requested clinic is null → queries become company-wide.
        // For clamped users this returns their own clinic id.
        Long clinicId = tenantScopeResolver.resolveClinicScope(null);

        LocalDate today = LocalDate.now();
        LocalDate windowEnd = today.plusDays(NEXT_DAYS - 1L);

        long activePatients = patientRepository.countByScope(companyId, clinicId);
        long activeEmployees = employeeRepository.countByScopeAndStatus(
                companyId, clinicId, EmployeeStatus.ACTIVE);
        long activeDoctors = employeeRepository.countByScopeAndTypeAndStatus(
                companyId, clinicId, EmployeeType.DOCTOR, EmployeeStatus.ACTIVE);
        long upcoming = appointmentRepository.countActiveInRange(
                companyId, clinicId, today, windowEnd);

        List<DailyBucket> weekBuckets = buildWeekBuckets(
                appointmentRepository.countActiveByDateInRange(companyId, clinicId, today, windowEnd),
                today);

        TodayBreakdown todayBreakdown = buildTodayBreakdown(
                appointmentRepository.countByStatusForDate(companyId, clinicId, today),
                today);

        List<RecentActivity> recent = auditLogRepository.findRecentByScope(
                companyId, clinicId, DASHBOARD_EVENT_TYPES,
                PageRequest.of(0, RECENT_ACTIVITY_LIMIT))
                .stream()
                .map(DashboardServiceImpl::toRecentActivity)
                .toList();

        return new DashboardSummaryResponse(
                new Scope(companyId, clinicId, clinicId == null),
                new Totals(activePatients, activeEmployees, activeDoctors, upcoming),
                todayBreakdown,
                weekBuckets,
                recent,
                Instant.now()
        );
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    /**
     * GROUP BY on appointmentDate returns only days that actually have rows;
     * the frontend wants a dense 7-entry array so charts render a flat "0"
     * bar on quiet days. Zero-fill the gaps here.
     */
    private static List<DailyBucket> buildWeekBuckets(List<Object[]> rows, LocalDate start) {
        Map<LocalDate, Long> byDay = new HashMap<>(rows.size() * 2);
        for (Object[] row : rows) {
            byDay.put((LocalDate) row[0], ((Number) row[1]).longValue());
        }
        DailyBucket[] buckets = new DailyBucket[NEXT_DAYS];
        for (int i = 0; i < NEXT_DAYS; i++) {
            LocalDate date = start.plusDays(i);
            buckets[i] = new DailyBucket(date, byDay.getOrDefault(date, 0L));
        }
        return Arrays.asList(buckets);
    }

    private static TodayBreakdown buildTodayBreakdown(List<Object[]> rows, LocalDate date) {
        EnumMap<AppointmentStatus, Long> byStatus = new EnumMap<>(AppointmentStatus.class);
        long total = 0;
        for (Object[] row : rows) {
            AppointmentStatus status = (AppointmentStatus) row[0];
            long count = ((Number) row[1]).longValue();
            byStatus.put(status, count);
            total += count;
        }
        return new TodayBreakdown(
                date,
                total,
                byStatus.getOrDefault(AppointmentStatus.CREATED, 0L),
                byStatus.getOrDefault(AppointmentStatus.CONFIRMED, 0L),
                byStatus.getOrDefault(AppointmentStatus.COMPLETED, 0L),
                byStatus.getOrDefault(AppointmentStatus.CANCELLED, 0L),
                byStatus.getOrDefault(AppointmentStatus.NO_SHOW, 0L)
        );
    }

    private static RecentActivity toRecentActivity(AuditLog a) {
        return new RecentActivity(
                a.getId(),
                a.getEventType(),
                a.getActorUserId(),
                a.getActorEmail(),
                a.getTargetType(),
                a.getTargetId(),
                a.getOccurredAt()
        );
    }
}
