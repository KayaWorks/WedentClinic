package com.wedent.clinic.dashboard;

import com.wedent.clinic.appointment.entity.AppointmentStatus;
import com.wedent.clinic.appointment.repository.AppointmentRepository;
import com.wedent.clinic.common.audit.entity.AuditLog;
import com.wedent.clinic.common.audit.event.AuditEventType;
import com.wedent.clinic.common.audit.repository.AuditLogRepository;
import com.wedent.clinic.common.tenant.TenantScopeResolver;
import com.wedent.clinic.dashboard.dto.DashboardSummaryResponse;
import com.wedent.clinic.dashboard.service.impl.DashboardServiceImpl;
import com.wedent.clinic.employee.entity.EmployeeStatus;
import com.wedent.clinic.employee.entity.EmployeeType;
import com.wedent.clinic.employee.repository.EmployeeRepository;
import com.wedent.clinic.patient.repository.PatientRepository;
import com.wedent.clinic.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for the dashboard aggregator. Repositories are mocked so we
 * exercise the stitching logic (zero-filling buckets, whitelisting event
 * types, scope resolution) without a real Postgres.
 */
class DashboardServiceImplTest {

    private PatientRepository patientRepository;
    private EmployeeRepository employeeRepository;
    private AppointmentRepository appointmentRepository;
    private AuditLogRepository auditLogRepository;
    private TenantScopeResolver scopeResolver;
    private DashboardServiceImpl service;

    @BeforeEach
    void setUp() {
        patientRepository = Mockito.mock(PatientRepository.class);
        employeeRepository = Mockito.mock(EmployeeRepository.class);
        appointmentRepository = Mockito.mock(AppointmentRepository.class);
        auditLogRepository = Mockito.mock(AuditLogRepository.class);
        scopeResolver = Mockito.mock(TenantScopeResolver.class);

        service = new DashboardServiceImpl(
                patientRepository, employeeRepository, appointmentRepository,
                auditLogRepository, scopeResolver);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void summary_ownerSeesCompanyWideScope_andZeroFillsMissingDays() {
        authenticate(new AuthenticatedUser(
                1L, "owner@x", 7L, null, Set.of("CLINIC_OWNER"), List.of()));
        // Owner: resolver returns null → queries are company-wide.
        when(scopeResolver.resolveClinicScope(null)).thenReturn(null);

        when(patientRepository.countByScope(7L, null)).thenReturn(42L);
        when(employeeRepository.countByScopeAndStatus(7L, null, EmployeeStatus.ACTIVE)).thenReturn(10L);
        when(employeeRepository.countByScopeAndTypeAndStatus(
                7L, null, EmployeeType.DOCTOR, EmployeeStatus.ACTIVE)).thenReturn(4L);
        when(appointmentRepository.countActiveInRange(eq(7L), eq(null), any(), any())).thenReturn(15L);

        // Only two days in the window actually have rows; the other 5 must
        // be zero-filled in the returned bucket list.
        LocalDate today = LocalDate.now();
        when(appointmentRepository.countActiveByDateInRange(eq(7L), eq(null), any(), any()))
                .thenReturn(List.of(
                        new Object[]{today, 3L},
                        new Object[]{today.plusDays(2), 5L}
                ));

        when(appointmentRepository.countByStatusForDate(eq(7L), eq(null), any()))
                .thenReturn(List.of(
                        new Object[]{AppointmentStatus.CONFIRMED, 2L},
                        new Object[]{AppointmentStatus.COMPLETED, 1L}
                ));

        when(auditLogRepository.findRecentByScope(eq(7L), eq(null), anyCollection(), any(Pageable.class)))
                .thenReturn(List.of(auditRow(100L, AuditEventType.APPOINTMENT_CREATED)));

        DashboardSummaryResponse resp = service.summary();

        assertThat(resp.scope().companyWide()).isTrue();
        assertThat(resp.scope().clinicId()).isNull();
        assertThat(resp.totals().activePatients()).isEqualTo(42);
        assertThat(resp.totals().activeDoctors()).isEqualTo(4);
        assertThat(resp.totals().upcomingAppointments()).isEqualTo(15);

        assertThat(resp.next7Days()).hasSize(7);
        assertThat(resp.next7Days().get(0).count()).isEqualTo(3);
        assertThat(resp.next7Days().get(1).count()).isZero();
        assertThat(resp.next7Days().get(2).count()).isEqualTo(5);

        assertThat(resp.today().total()).isEqualTo(3);
        assertThat(resp.today().confirmed()).isEqualTo(2);
        assertThat(resp.today().completed()).isEqualTo(1);
        assertThat(resp.today().cancelled()).isZero();

        assertThat(resp.recentActivity()).hasSize(1);
        assertThat(resp.recentActivity().get(0).type())
                .isEqualTo(AuditEventType.APPOINTMENT_CREATED.name());
    }

    @Test
    void summary_nonOwnerIsClampedToOwnClinic() {
        authenticate(new AuthenticatedUser(
                2L, "doc@x", 7L, 11L, Set.of("DOCTOR"), List.of()));
        // Non-owner: resolver returns their clinic id.
        when(scopeResolver.resolveClinicScope(null)).thenReturn(11L);

        when(patientRepository.countByScope(7L, 11L)).thenReturn(8L);
        when(employeeRepository.countByScopeAndStatus(7L, 11L, EmployeeStatus.ACTIVE)).thenReturn(3L);
        when(employeeRepository.countByScopeAndTypeAndStatus(
                7L, 11L, EmployeeType.DOCTOR, EmployeeStatus.ACTIVE)).thenReturn(2L);
        when(appointmentRepository.countActiveInRange(eq(7L), eq(11L), any(), any())).thenReturn(4L);
        when(appointmentRepository.countActiveByDateInRange(eq(7L), eq(11L), any(), any()))
                .thenReturn(List.of());
        when(appointmentRepository.countByStatusForDate(eq(7L), eq(11L), any()))
                .thenReturn(List.of());
        when(auditLogRepository.findRecentByScope(eq(7L), eq(11L), anyCollection(), any(Pageable.class)))
                .thenReturn(List.of());

        DashboardSummaryResponse resp = service.summary();

        assertThat(resp.scope().companyWide()).isFalse();
        assertThat(resp.scope().clinicId()).isEqualTo(11L);
        assertThat(resp.totals().activePatients()).isEqualTo(8);
        // Empty window still produces 7 zero-buckets (for dense charting FE-side).
        assertThat(resp.next7Days()).hasSize(7);
        assertThat(resp.next7Days()).allMatch(b -> b.count() == 0L);
        assertThat(resp.today().total()).isZero();
    }

    // ─────────────────────────────────────────────────────────────────────────

    private static void authenticate(AuthenticatedUser principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "n/a", principal.authorities()));
    }

    private static AuditLog auditRow(long id, AuditEventType type) {
        AuditLog a = AuditLog.builder()
                .eventType(type.name())
                .actorUserId(99L)
                .actorEmail("actor@x")
                .targetType("Appointment")
                .targetId(200L)
                .occurredAt(Instant.now())
                .build();
        a.setId(id);
        return a;
    }
}
