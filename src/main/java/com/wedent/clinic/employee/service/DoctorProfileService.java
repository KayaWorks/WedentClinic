package com.wedent.clinic.employee.service;

import com.wedent.clinic.employee.dto.DoctorProfileRequest;
import com.wedent.clinic.employee.dto.DoctorProfileResponse;

public interface DoctorProfileService {

    DoctorProfileResponse upsert(Long employeeId, DoctorProfileRequest request);

    DoctorProfileResponse getByEmployeeId(Long employeeId);
}
