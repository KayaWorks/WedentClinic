package com.wedent.clinic.employee.service.impl;

import com.wedent.clinic.clinic.entity.Clinic;
import com.wedent.clinic.clinic.repository.ClinicRepository;
import com.wedent.clinic.common.dto.PageResponse;
import com.wedent.clinic.common.exception.DuplicateResourceException;
import com.wedent.clinic.common.exception.ResourceNotFoundException;
import com.wedent.clinic.common.tenant.TenantScopeResolver;
import com.wedent.clinic.employee.dto.EmployeeCreateRequest;
import com.wedent.clinic.employee.dto.EmployeeResponse;
import com.wedent.clinic.employee.dto.EmployeeUpdateRequest;
import com.wedent.clinic.employee.entity.Employee;
import com.wedent.clinic.employee.entity.EmployeeStatus;
import com.wedent.clinic.employee.entity.EmployeeType;
import com.wedent.clinic.employee.mapper.EmployeeMapper;
import com.wedent.clinic.employee.repository.EmployeeRepository;
import com.wedent.clinic.employee.service.EmployeeService;
import com.wedent.clinic.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class EmployeeServiceImpl implements EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final ClinicRepository clinicRepository;
    @Qualifier("employeeMapperImpl")
    private final EmployeeMapper employeeMapper;
    private final TenantScopeResolver tenantScopeResolver;

    @Override
    public EmployeeResponse create(EmployeeCreateRequest request) {
        Long companyId = SecurityUtils.currentCompanyId();
        Clinic clinic = loadClinicForCompany(request.clinicId(), companyId);
        SecurityUtils.verifyClinicAccess(clinic.getId());

        if (employeeRepository.existsByClinicIdAndEmailIgnoreCase(clinic.getId(), request.email())) {
            throw new DuplicateResourceException(
                    "Email already exists in clinic: %s".formatted(request.email()));
        }

        Employee entity = employeeMapper.toEntity(request);
        entity.setCompany(clinic.getCompany());
        entity.setClinic(clinic);
        entity.setStatus(EmployeeStatus.ACTIVE);

        Employee saved = employeeRepository.save(entity);
        return employeeMapper.toResponse(saved);
    }

    @Override
    public EmployeeResponse update(Long id, EmployeeUpdateRequest request) {
        Employee employee = loadInScope(id);
        SecurityUtils.verifyClinicAccess(employee.getClinic().getId());

        boolean emailChanged = !employee.getEmail().equalsIgnoreCase(request.email());
        if (emailChanged && employeeRepository
                .existsByClinicIdAndEmailIgnoreCaseAndIdNot(employee.getClinic().getId(), request.email(), id)) {
            throw new DuplicateResourceException(
                    "Email already exists in clinic: %s".formatted(request.email()));
        }

        employeeMapper.updateEntity(request, employee);
        return employeeMapper.toResponse(employee);
    }

    @Override
    @Transactional(readOnly = true)
    public EmployeeResponse getById(Long id) {
        Employee employee = loadInScope(id);
        SecurityUtils.verifyClinicAccess(employee.getClinic().getId());
        return employeeMapper.toResponse(employee);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<EmployeeResponse> search(Long clinicId, EmployeeType type, Pageable pageable) {
        Long companyId = SecurityUtils.currentCompanyId();
        Long effectiveClinicId = tenantScopeResolver.resolveClinicScope(clinicId);
        Page<EmployeeResponse> page = employeeRepository
                .search(companyId, effectiveClinicId, type, pageable)
                .map(employeeMapper::toResponse);
        return PageResponse.of(page);
    }

    @Override
    public void delete(Long id) {
        Employee employee = loadInScope(id);
        SecurityUtils.verifyClinicAccess(employee.getClinic().getId());
        employeeRepository.delete(employee);
    }

    private Employee loadInScope(Long id) {
        Long companyId = SecurityUtils.currentCompanyId();
        return employeeRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", id));
    }

    private Clinic loadClinicForCompany(Long clinicId, Long companyId) {
        return clinicRepository.findByIdAndCompanyId(clinicId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Clinic", clinicId));
    }
}
