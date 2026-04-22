package com.wedent.clinic.appointment;

import com.wedent.clinic.appointment.dto.AppointmentCreateRequest;
import com.wedent.clinic.appointment.entity.Appointment;
import com.wedent.clinic.appointment.entity.AppointmentStatus;
import com.wedent.clinic.appointment.mapper.AppointmentMapper;
import com.wedent.clinic.appointment.repository.AppointmentRepository;
import com.wedent.clinic.appointment.service.impl.AppointmentServiceImpl;
import com.wedent.clinic.clinic.entity.Clinic;
import com.wedent.clinic.clinic.repository.ClinicRepository;
import com.wedent.clinic.common.audit.AuditEventPublisher;
import com.wedent.clinic.common.exception.AppointmentConflictException;
import com.wedent.clinic.common.tenant.TenantScopeResolver;
import com.wedent.clinic.company.entity.Company;
import com.wedent.clinic.employee.entity.Employee;
import com.wedent.clinic.employee.entity.EmployeeStatus;
import com.wedent.clinic.employee.entity.EmployeeType;
import com.wedent.clinic.employee.repository.EmployeeRepository;
import com.wedent.clinic.patient.entity.Patient;
import com.wedent.clinic.patient.repository.PatientRepository;
import com.wedent.clinic.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class AppointmentConflictTest {

    private AppointmentRepository appointmentRepository;
    private PatientRepository patientRepository;
    private EmployeeRepository employeeRepository;
    private ClinicRepository clinicRepository;
    private AppointmentMapper appointmentMapper;
    private TenantScopeResolver tenantScopeResolver;
    private AuditEventPublisher auditEventPublisher;
    private AppointmentServiceImpl service;

    @BeforeEach
    void setUp() {
        appointmentRepository = Mockito.mock(AppointmentRepository.class);
        patientRepository = Mockito.mock(PatientRepository.class);
        employeeRepository = Mockito.mock(EmployeeRepository.class);
        clinicRepository = Mockito.mock(ClinicRepository.class);
        appointmentMapper = Mockito.mock(AppointmentMapper.class);
        tenantScopeResolver = Mockito.mock(TenantScopeResolver.class);
        auditEventPublisher = Mockito.mock(AuditEventPublisher.class);
        service = new AppointmentServiceImpl(
                appointmentRepository,
                patientRepository,
                employeeRepository,
                clinicRepository,
                appointmentMapper,
                tenantScopeResolver,
                auditEventPublisher);

        AuthenticatedUser principal = new AuthenticatedUser(
                1L, "owner@example.com", 100L, null, Set.of("CLINIC_OWNER"), List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.authorities()));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void create_overlappingSlot_throwsConflict() {
        Company company = Company.builder().name("Acme").build();
        company.setId(100L);

        Clinic clinic = Clinic.builder().company(company).name("Main").build();
        clinic.setId(7L);

        Patient patient = Patient.builder()
                .firstName("John").lastName("Doe").phone("+901112223344")
                .company(company).clinic(clinic).build();
        patient.setId(50L);

        Employee doctor = Employee.builder()
                .firstName("Anna").lastName("Smith").email("a@b.c")
                .company(company).clinic(clinic)
                .employeeType(EmployeeType.DOCTOR).status(EmployeeStatus.ACTIVE).build();
        doctor.setId(20L);

        when(clinicRepository.findByIdAndCompanyId(7L, 100L)).thenReturn(Optional.of(clinic));
        when(patientRepository.findByIdAndCompanyId(50L, 100L)).thenReturn(Optional.of(patient));
        when(employeeRepository.findByIdAndCompanyIdAndEmployeeType(20L, 100L, EmployeeType.DOCTOR))
                .thenReturn(Optional.of(doctor));

        Appointment existing = Appointment.builder()
                .doctor(doctor).clinic(clinic).company(company).patient(patient)
                .appointmentDate(LocalDate.of(2026, 5, 1))
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(10, 30))
                .status(AppointmentStatus.CREATED)
                .build();

        when(appointmentRepository.findConflictsForUpdate(
                ArgumentMatchers.eq(20L),
                ArgumentMatchers.eq(LocalDate.of(2026, 5, 1)),
                any(LocalTime.class), any(LocalTime.class), ArgumentMatchers.isNull()))
                .thenReturn(List.of(existing));

        AppointmentCreateRequest request = new AppointmentCreateRequest(
                7L, 50L, 20L,
                LocalDate.of(2026, 5, 1),
                LocalTime.of(10, 15),
                LocalTime.of(10, 45),
                "Overlapping");

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(AppointmentConflictException.class);
    }

    @Test
    void create_endBeforeStart_throwsValidation() {
        AppointmentCreateRequest request = new AppointmentCreateRequest(
                7L, 50L, 20L,
                LocalDate.of(2026, 5, 1),
                LocalTime.of(11, 0),
                LocalTime.of(10, 0),
                "Bad slot");

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(com.wedent.clinic.common.exception.BusinessException.class)
                .hasMessageContaining("endTime must be after startTime");
    }
}
