package com.wedent.clinic.appointment.repository;

import com.wedent.clinic.appointment.entity.Appointment;
import com.wedent.clinic.appointment.entity.AppointmentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    /**
     * Postgres transaction-scoped advisory lock keyed on doctor+date. Serializes
     * all concurrent booking attempts for the same doctor/day so the subsequent
     * conflict check is race-safe even when no rows yet exist (plain SELECT FOR
     * UPDATE cannot lock an empty range in Postgres).
     *
     * <p>Released automatically on commit/rollback. Return value is unused —
     * {@code pg_advisory_xact_lock} is a {@code void}-returning function, we
     * just need the side effect of the lock being acquired.
     */
    @Query(value = "SELECT pg_advisory_xact_lock(:key)", nativeQuery = true)
    Object acquireDoctorDayLock(@Param("key") long key);

    @EntityGraph(attributePaths = {"patient", "doctor", "clinic"})
    Optional<Appointment> findByIdAndCompanyId(Long id, Long companyId);

    /**
     * Pessimistic lock to serialize conflict checks for the same doctor/date.
     * The list returned will be inspected for actual time overlap in service layer.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT a FROM Appointment a
            WHERE a.doctor.id = :doctorId
              AND a.appointmentDate = :date
              AND a.status IN (com.wedent.clinic.appointment.entity.AppointmentStatus.CREATED,
                               com.wedent.clinic.appointment.entity.AppointmentStatus.CONFIRMED)
              AND a.startTime < :endTime
              AND a.endTime   > :startTime
              AND (:excludeId IS NULL OR a.id <> :excludeId)
            """)
    List<Appointment> findConflictsForUpdate(Long doctorId,
                                             LocalDate date,
                                             LocalTime startTime,
                                             LocalTime endTime,
                                             Long excludeId);

    @Query("""
            SELECT a FROM Appointment a
            WHERE a.company.id = :companyId
              AND (:clinicId  IS NULL OR a.clinic.id  = :clinicId)
              AND (:doctorId  IS NULL OR a.doctor.id  = :doctorId)
              AND (:patientId IS NULL OR a.patient.id = :patientId)
              AND (:date      IS NULL OR a.appointmentDate = :date)
              AND (:status    IS NULL OR a.status = :status)
            """)
    @EntityGraph(attributePaths = {"patient", "doctor"})
    Page<Appointment> search(Long companyId,
                             Long clinicId,
                             Long doctorId,
                             Long patientId,
                             LocalDate date,
                             AppointmentStatus status,
                             Pageable pageable);
}
