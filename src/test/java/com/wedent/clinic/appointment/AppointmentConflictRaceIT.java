package com.wedent.clinic.appointment;

import com.wedent.clinic.appointment.dto.AppointmentCreateRequest;
import com.wedent.clinic.appointment.service.AppointmentService;
import com.wedent.clinic.clinic.entity.Clinic;
import com.wedent.clinic.clinic.repository.ClinicRepository;
import com.wedent.clinic.common.exception.AppointmentConflictException;
import com.wedent.clinic.company.entity.Company;
import com.wedent.clinic.company.repository.CompanyRepository;
import com.wedent.clinic.employee.entity.Employee;
import com.wedent.clinic.employee.entity.EmployeeStatus;
import com.wedent.clinic.employee.entity.EmployeeType;
import com.wedent.clinic.employee.repository.EmployeeRepository;
import com.wedent.clinic.patient.entity.Gender;
import com.wedent.clinic.patient.entity.Patient;
import com.wedent.clinic.patient.repository.PatientRepository;
import com.wedent.clinic.security.AuthenticatedUser;
import com.wedent.clinic.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end proof that appointment conflict detection is race-safe under real
 * PostgreSQL semantics (not H2 emulation). Two transactions try to book the
 * same doctor/date/time in parallel — the advisory xact lock in the service
 * layer must serialize them so exactly one commits and the other sees the
 * first row and is rejected with a conflict.
 *
 * <p>H2 cannot faithfully reproduce this, so this test runs only against the
 * Testcontainers Postgres (see {@link AbstractPostgresIntegrationTest}).
 */
class AppointmentConflictRaceIT extends AbstractPostgresIntegrationTest {

    @Autowired private CompanyRepository companyRepository;
    @Autowired private ClinicRepository clinicRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private AppointmentService appointmentService;
    @Autowired private TransactionTemplate txTemplate;

    private Company company;
    private Clinic clinic;
    private Employee doctor;
    private Patient patientA;
    private Patient patientB;

    @BeforeEach
    void seed() {
        company = companyRepository.save(Company.builder()
                .name("Race Test Co " + System.nanoTime())
                .build());

        clinic = clinicRepository.save(Clinic.builder()
                .company(company).name("Main").build());

        doctor = employeeRepository.save(Employee.builder()
                .company(company).clinic(clinic)
                .firstName("Anna").lastName("Smith")
                .email("anna-%d@example.com".formatted(System.nanoTime()))
                .employeeType(EmployeeType.DOCTOR)
                .status(EmployeeStatus.ACTIVE)
                .build());

        patientA = patientRepository.save(Patient.builder()
                .company(company).clinic(clinic)
                .firstName("Pat").lastName("A")
                .phone("+9050%08d".formatted((int) (Math.random() * 99_999_999)))
                .gender(Gender.FEMALE).build());

        patientB = patientRepository.save(Patient.builder()
                .company(company).clinic(clinic)
                .firstName("Pat").lastName("B")
                .phone("+9051%08d".formatted((int) (Math.random() * 99_999_999)))
                .gender(Gender.MALE).build());

        setAuthentication();
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void twoParallelBookings_sameDoctorAndOverlappingSlot_exactlyOneSucceeds() throws Exception {
        LocalDate date = LocalDate.now().plusDays(30);
        AppointmentCreateRequest reqA = new AppointmentCreateRequest(
                clinic.getId(), patientA.getId(), doctor.getId(),
                date, LocalTime.of(10, 0), LocalTime.of(10, 30), "A");
        AppointmentCreateRequest reqB = new AppointmentCreateRequest(
                clinic.getId(), patientB.getId(), doctor.getId(),
                date, LocalTime.of(10, 15), LocalTime.of(10, 45), "B");

        AtomicInteger successes = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        CountDownLatch startGate = new CountDownLatch(1);
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        var executor = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<Void> f1 = CompletableFuture.runAsync(
                    () -> runIsolated(auth, reqA, startGate, successes, conflicts), executor);
            CompletableFuture<Void> f2 = CompletableFuture.runAsync(
                    () -> runIsolated(auth, reqB, startGate, successes, conflicts), executor);
            startGate.countDown(); // release both threads simultaneously
            CompletableFuture.allOf(f1, f2).get(30, TimeUnit.SECONDS);
        } finally {
            executor.shutdown();
        }

        assertThat(successes.get())
                .as("exactly one booking must win the race")
                .isEqualTo(1);
        assertThat(conflicts.get())
                .as("the losing booking must surface AppointmentConflictException")
                .isEqualTo(1);
    }

    @Test
    void nonOverlappingSlots_bothSucceed() {
        LocalDate date = LocalDate.now().plusDays(31);
        appointmentService.create(new AppointmentCreateRequest(
                clinic.getId(), patientA.getId(), doctor.getId(),
                date, LocalTime.of(9, 0), LocalTime.of(9, 30), "A"));
        appointmentService.create(new AppointmentCreateRequest(
                clinic.getId(), patientB.getId(), doctor.getId(),
                date, LocalTime.of(9, 30), LocalTime.of(10, 0), "B"));
        // implicit assertion: no exception thrown
    }

    private void runIsolated(Authentication auth,
                             AppointmentCreateRequest request,
                             CountDownLatch startGate,
                             AtomicInteger successes,
                             AtomicInteger conflicts) {
        try {
            SecurityContextHolder.getContext().setAuthentication(auth);
            startGate.await();
            txTemplate.executeWithoutResult(status -> {
                try {
                    appointmentService.create(request);
                    successes.incrementAndGet();
                } catch (AppointmentConflictException e) {
                    conflicts.incrementAndGet();
                    status.setRollbackOnly();
                }
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void setAuthentication() {
        AuthenticatedUser principal = new AuthenticatedUser(
                1L, "owner@example.com", company.getId(), clinic.getId(),
                Set.of("CLINIC_OWNER"), List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.authorities()));
    }
}
