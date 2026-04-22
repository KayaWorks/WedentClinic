package com.wedent.clinic.employee.service.impl;

import com.wedent.clinic.common.exception.BusinessException;
import com.wedent.clinic.common.exception.ErrorCode;
import com.wedent.clinic.common.exception.ResourceNotFoundException;
import com.wedent.clinic.employee.dto.DoctorProfileRequest;
import com.wedent.clinic.employee.dto.DoctorProfileResponse;
import com.wedent.clinic.employee.entity.DoctorProfile;
import com.wedent.clinic.employee.entity.Employee;
import com.wedent.clinic.employee.entity.EmployeeType;
import com.wedent.clinic.employee.mapper.EmployeeMapper;
import com.wedent.clinic.employee.repository.DoctorProfileRepository;
import com.wedent.clinic.employee.repository.EmployeeRepository;
import com.wedent.clinic.employee.service.DoctorProfileService;
import com.wedent.clinic.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class DoctorProfileServiceImpl implements DoctorProfileService {

    private final DoctorProfileRepository doctorProfileRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeMapper employeeMapper;

    @Override
    public DoctorProfileResponse upsert(Long employeeId, DoctorProfileRequest request) {
        Employee doctor = loadDoctor(employeeId);
        SecurityUtils.verifyClinicAccess(doctor.getClinic().getId());

        DoctorProfile profile = doctorProfileRepository.findByEmployeeId(employeeId)
                .orElseGet(() -> {
                    DoctorProfile created = employeeMapper.toDoctorProfile(request);
                    created.setEmployee(doctor);
                    return created;
                });

        employeeMapper.updateDoctorProfile(request, profile);
        DoctorProfile saved = doctorProfileRepository.save(profile);
        return employeeMapper.toDoctorProfileResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public DoctorProfileResponse getByEmployeeId(Long employeeId) {
        Employee doctor = loadDoctor(employeeId);
        SecurityUtils.verifyClinicAccess(doctor.getClinic().getId());
        return doctorProfileRepository.findByEmployeeId(employeeId)
                .map(employeeMapper::toDoctorProfileResponse)
                .orElseThrow(() -> new ResourceNotFoundException("DoctorProfile for employee", employeeId));
    }

    private Employee loadDoctor(Long employeeId) {
        Long companyId = SecurityUtils.currentCompanyId();
        Employee employee = employeeRepository.findByIdAndCompanyId(employeeId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", employeeId));
        if (employee.getEmployeeType() != EmployeeType.DOCTOR) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Doctor profile can only be assigned to DOCTOR-type employees");
        }
        return employee;
    }
}
