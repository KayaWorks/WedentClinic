package com.wedent.clinic.appointment.service;

import com.wedent.clinic.appointment.dto.AppointmentCreateRequest;
import com.wedent.clinic.appointment.dto.AppointmentResponse;
import com.wedent.clinic.appointment.dto.AppointmentStatusUpdateRequest;
import com.wedent.clinic.appointment.dto.AppointmentUpdateRequest;
import com.wedent.clinic.appointment.dto.CalendarAppointmentResponse;
import com.wedent.clinic.appointment.entity.AppointmentStatus;
import com.wedent.clinic.common.dto.PageResponse;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

public interface AppointmentService {

    AppointmentResponse create(AppointmentCreateRequest request);

    AppointmentResponse update(Long id, AppointmentUpdateRequest request);

    AppointmentResponse changeStatus(Long id, AppointmentStatusUpdateRequest request);

    AppointmentResponse getById(Long id);

    PageResponse<AppointmentResponse> search(Long clinicId,
                                             Long doctorId,
                                             Long patientId,
                                             LocalDate date,
                                             AppointmentStatus status,
                                             Pageable pageable);

    void delete(Long id);

    /**
     * Calendar range query.
     *
     * <p>Returns every appointment whose {@code appointmentDate} falls
     * within {@code [start, end]} (inclusive), pre-shaped as
     * {@link CalendarAppointmentResponse}. Role-scoped server-side:
     * {@code CLINIC_OWNER} sees the whole company, clinic-bound roles
     * see only their clinic, {@code DOCTOR} only their own.</p>
     *
     * <p>By default cancelled rows are excluded. Pass an explicit
     * {@code status} of {@code CANCELLED} to include them.</p>
     *
     * <p>The service enforces a 370-day cap on {@code end - start} to
     * guard against accidental "all appointments ever" pulls.</p>
     */
    List<CalendarAppointmentResponse> calendar(LocalDate start,
                                               LocalDate end,
                                               Long doctorId,
                                               Long clinicId,
                                               AppointmentStatus status);
}
