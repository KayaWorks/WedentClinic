package com.wedent.clinic.appointment.service.impl;

import com.wedent.clinic.appointment.dto.AppointmentCreateRequest;
import com.wedent.clinic.appointment.dto.AppointmentResponse;
import com.wedent.clinic.appointment.dto.AppointmentStatusUpdateRequest;
import com.wedent.clinic.appointment.dto.AppointmentUpdateRequest;
import com.wedent.clinic.appointment.entity.Appointment;
import com.wedent.clinic.appointment.entity.AppointmentStatus;
import com.wedent.clinic.appointment.mapper.AppointmentMapper;
import com.wedent.clinic.appointment.repository.AppointmentRepository;
import com.wedent.clinic.appointment.service.AppointmentService;
import com.wedent.clinic.clinic.entity.Clinic;
import com.wedent.clinic.clinic.repository.ClinicRepository;
import com.wedent.clinic.common.audit.AuditEventPublisher;
import com.wedent.clinic.common.audit.event.AuditEvent;
import com.wedent.clinic.common.audit.event.AuditEventType;
import com.wedent.clinic.common.dto.PageResponse;
import com.wedent.clinic.common.exception.AppointmentConflictException;
import com.wedent.clinic.common.exception.BusinessException;
import com.wedent.clinic.common.exception.ErrorCode;
import com.wedent.clinic.common.exception.ResourceNotFoundException;
import com.wedent.clinic.common.tenant.TenantScopeResolver;
import com.wedent.clinic.employee.entity.Employee;
import com.wedent.clinic.employee.entity.EmployeeStatus;
import com.wedent.clinic.employee.entity.EmployeeType;
import com.wedent.clinic.employee.repository.EmployeeRepository;
import com.wedent.clinic.patient.entity.Patient;
import com.wedent.clinic.patient.repository.PatientRepository;
import com.wedent.clinic.security.SecurityUtils;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class AppointmentServiceImpl implements AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final EmployeeRepository employeeRepository;
    private final ClinicRepository clinicRepository;
    private final AppointmentMapper appointmentMapper;
    private final TenantScopeResolver tenantScopeResolver;
    private final AuditEventPublisher auditEventPublisher;

    @Override
    @Timed(value = "wedent.appointment.create",
            description = "Time to create an appointment end-to-end incl. conflict check",
            percentiles = {0.5, 0.95, 0.99},
            histogram = true)
    public AppointmentResponse create(AppointmentCreateRequest request) {
        Long companyId = SecurityUtils.currentCompanyId();
        validateTimeRange(request.startTime(), request.endTime());

        Clinic clinic = loadClinicForCompany(request.clinicId(), companyId);
        SecurityUtils.verifyClinicAccess(clinic.getId());

        Patient patient = loadPatientForCompany(request.patientId(), companyId);
        Employee doctor = loadDoctorForCompany(request.doctorEmployeeId(), companyId);

        verifyPatientInClinic(patient, clinic.getId());
        verifyDoctorInClinic(doctor, clinic.getId());

        // Pessimistic lock + overlap check (race-safe)
        assertNoConflict(doctor.getId(), request.appointmentDate(), request.startTime(), request.endTime(), null);

        Appointment appointment = Appointment.builder()
                .company(clinic.getCompany())
                .clinic(clinic)
                .patient(patient)
                .doctor(doctor)
                .appointmentDate(request.appointmentDate())
                .startTime(request.startTime())
                .endTime(request.endTime())
                .status(AppointmentStatus.CREATED)
                .note(request.note())
                .build();

        Appointment saved = appointmentRepository.save(appointment);

        auditEventPublisher.publish(AuditEvent.builder(AuditEventType.APPOINTMENT_CREATED)
                .actorUserId(SecurityUtils.currentUserIdOrNull())
                .companyId(companyId)
                .clinicId(clinic.getId())
                .targetType("Appointment")
                .targetId(saved.getId())
                .detail(Map.of(
                        "patientId", saved.getPatient().getId(),
                        "doctorId", saved.getDoctor().getId(),
                        "date", saved.getAppointmentDate().toString()
                ))
                .build());

        return appointmentMapper.toResponse(saved);
    }

    @Override
    public AppointmentResponse update(Long id, AppointmentUpdateRequest request) {
        Long companyId = SecurityUtils.currentCompanyId();
        validateTimeRange(request.startTime(), request.endTime());

        Appointment appointment = loadInScope(id, companyId);
        SecurityUtils.verifyClinicAccess(appointment.getClinic().getId());

        if (!appointment.getStatus().isActiveSlot()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Cannot modify appointment in terminal status: " + appointment.getStatus());
        }

        Employee doctor = appointment.getDoctor();
        if (!doctor.getId().equals(request.doctorEmployeeId())) {
            doctor = loadDoctorForCompany(request.doctorEmployeeId(), companyId);
            verifyDoctorInClinic(doctor, appointment.getClinic().getId());
        }

        assertNoConflict(doctor.getId(), request.appointmentDate(), request.startTime(), request.endTime(), id);

        appointment.setDoctor(doctor);
        appointment.setAppointmentDate(request.appointmentDate());
        appointment.setStartTime(request.startTime());
        appointment.setEndTime(request.endTime());
        appointment.setNote(request.note());

        auditEventPublisher.publish(AuditEvent.builder(AuditEventType.APPOINTMENT_UPDATED)
                .actorUserId(SecurityUtils.currentUserIdOrNull())
                .companyId(companyId)
                .clinicId(appointment.getClinic().getId())
                .targetType("Appointment")
                .targetId(appointment.getId())
                .detail(Map.of(
                        "doctorId", doctor.getId(),
                        "date", appointment.getAppointmentDate().toString()
                ))
                .build());

        return appointmentMapper.toResponse(appointment);
    }

    @Override
    public AppointmentResponse changeStatus(Long id, AppointmentStatusUpdateRequest request) {
        Long companyId = SecurityUtils.currentCompanyId();
        Appointment appointment = loadInScope(id, companyId);
        SecurityUtils.verifyClinicAccess(appointment.getClinic().getId());

        AppointmentStatus current = appointment.getStatus();
        AppointmentStatus next = request.status();

        if (current == next) {
            return appointmentMapper.toResponse(appointment);
        }
        if (!current.canTransitionTo(next)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Illegal status transition: %s -> %s".formatted(current, next));
        }
        appointment.setStatus(next);

        auditEventPublisher.publish(AuditEvent.builder(AuditEventType.APPOINTMENT_STATUS_CHANGED)
                .actorUserId(SecurityUtils.currentUserIdOrNull())
                .companyId(companyId)
                .clinicId(appointment.getClinic().getId())
                .targetType("Appointment")
                .targetId(appointment.getId())
                .detail(Map.of("from", current.name(), "to", next.name()))
                .build());

        return appointmentMapper.toResponse(appointment);
    }

    @Override
    @Transactional(readOnly = true)
    public AppointmentResponse getById(Long id) {
        Long companyId = SecurityUtils.currentCompanyId();
        Appointment appointment = loadInScope(id, companyId);
        SecurityUtils.verifyClinicAccess(appointment.getClinic().getId());
        return appointmentMapper.toResponse(appointment);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AppointmentResponse> search(Long clinicId,
                                                    Long doctorId,
                                                    Long patientId,
                                                    LocalDate date,
                                                    AppointmentStatus status,
                                                    Pageable pageable) {
        Long companyId = SecurityUtils.currentCompanyId();
        Long effectiveClinicId = tenantScopeResolver.resolveClinicScope(clinicId);

        Page<AppointmentResponse> page = appointmentRepository
                .search(companyId, effectiveClinicId, doctorId, patientId, date, status, pageable)
                .map(appointmentMapper::toResponse);
        return PageResponse.of(page);
    }

    @Override
    public void delete(Long id) {
        Long companyId = SecurityUtils.currentCompanyId();
        Appointment appointment = loadInScope(id, companyId);
        SecurityUtils.verifyClinicAccess(appointment.getClinic().getId());

        Long clinicId = appointment.getClinic().getId();
        appointmentRepository.delete(appointment);

        auditEventPublisher.publish(AuditEvent.builder(AuditEventType.APPOINTMENT_DELETED)
                .actorUserId(SecurityUtils.currentUserIdOrNull())
                .companyId(companyId)
                .clinicId(clinicId)
                .targetType("Appointment")
                .targetId(id)
                .build());
    }

    // ----------------- Helpers -----------------

    private Appointment loadInScope(Long id, Long companyId) {
        return appointmentRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", id));
    }

    private Clinic loadClinicForCompany(Long clinicId, Long companyId) {
        return clinicRepository.findByIdAndCompanyId(clinicId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Clinic", clinicId));
    }

    private Patient loadPatientForCompany(Long patientId, Long companyId) {
        return patientRepository.findByIdAndCompanyId(patientId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", patientId));
    }

    private Employee loadDoctorForCompany(Long doctorId, Long companyId) {
        Employee employee = employeeRepository
                .findByIdAndCompanyIdAndEmployeeType(doctorId, companyId, EmployeeType.DOCTOR)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor", doctorId));
        if (employee.getStatus() != EmployeeStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Doctor is not active: " + doctorId);
        }
        return employee;
    }

    private void verifyPatientInClinic(Patient patient, Long clinicId) {
        if (!patient.getClinic().getId().equals(clinicId)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Patient does not belong to clinic " + clinicId);
        }
    }

    private void verifyDoctorInClinic(Employee doctor, Long clinicId) {
        if (!doctor.getClinic().getId().equals(clinicId)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Doctor does not belong to clinic " + clinicId);
        }
    }

    private void validateTimeRange(LocalTime start, LocalTime end) {
        if (!end.isAfter(start)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "endTime must be after startTime");
        }
    }

    private void assertNoConflict(Long doctorId,
                                  LocalDate date,
                                  LocalTime startTime,
                                  LocalTime endTime,
                                  Long excludeId) {
        // Advisory xact lock serializes bookings for the same doctor/day across
        // transactions — plain SELECT FOR UPDATE cannot lock empty ranges in
        // Postgres, so two concurrent first-bookings could otherwise both slip
        // through reporting "no conflicts". The lock is released automatically
        // on commit/rollback.
        appointmentRepository.acquireDoctorDayLock(doctorDayLockKey(doctorId, date));

        List<Appointment> conflicts = appointmentRepository
                .findConflictsForUpdate(doctorId, date, startTime, endTime, excludeId);
        if (!conflicts.isEmpty()) {
            throw new AppointmentConflictException(
                    "Doctor already has an appointment in the requested time slot");
        }
    }

    /**
     * Deterministic 64-bit key combining doctor id (high 32 bits) and epoch-day
     * (low 32 bits). Collision-free as long as doctor ids fit in a signed int.
     */
    private static long doctorDayLockKey(Long doctorId, LocalDate date) {
        long d = doctorId == null ? 0L : doctorId;
        long day = date.toEpochDay();
        return (d << 32) ^ (day & 0xffffffffL);
    }
}
