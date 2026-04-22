package com.wedent.clinic.employee.service;

import com.wedent.clinic.common.dto.PageResponse;
import com.wedent.clinic.employee.dto.EmployeeCreateRequest;
import com.wedent.clinic.employee.dto.EmployeeResponse;
import com.wedent.clinic.employee.dto.EmployeeUpdateRequest;
import com.wedent.clinic.employee.entity.EmployeeType;
import org.springframework.data.domain.Pageable;

public interface EmployeeService {

    EmployeeResponse create(EmployeeCreateRequest request);

    EmployeeResponse update(Long id, EmployeeUpdateRequest request);

    EmployeeResponse getById(Long id);

    PageResponse<EmployeeResponse> search(Long clinicId, EmployeeType type, Pageable pageable);

    void delete(Long id);
}
