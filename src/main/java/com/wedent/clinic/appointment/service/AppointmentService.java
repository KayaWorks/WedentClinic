package com.wedent.clinic.appointment.service;

import com.wedent.clinic.appointment.dto.AppointmentCreateRequest;
import com.wedent.clinic.appointment.dto.AppointmentResponse;
import com.wedent.clinic.appointment.dto.AppointmentStatusUpdateRequest;
import com.wedent.clinic.appointment.dto.AppointmentUpdateRequest;
import com.wedent.clinic.appointment.entity.AppointmentStatus;
import com.wedent.clinic.common.dto.PageResponse;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

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
}
