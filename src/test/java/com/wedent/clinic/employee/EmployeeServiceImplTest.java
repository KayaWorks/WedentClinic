package com.wedent.clinic.employee;

import com.wedent.clinic.clinic.entity.Clinic;
import com.wedent.clinic.clinic.repository.ClinicRepository;
import com.wedent.clinic.common.exception.DuplicateResourceException;
import com.wedent.clinic.common.tenant.TenantScopeResolver;
import com.wedent.clinic.company.entity.Company;
import com.wedent.clinic.employee.dto.EmployeeCreateRequest;
import com.wedent.clinic.employee.entity.Employee;
import com.wedent.clinic.employee.entity.EmployeeType;
import com.wedent.clinic.employee.mapper.EmployeeMapper;
import com.wedent.clinic.employee.repository.EmployeeRepository;
import com.wedent.clinic.employee.service.impl.EmployeeServiceImpl;
import com.wedent.clinic.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

class EmployeeServiceImplTest {

    private EmployeeRepository employeeRepository;
    private ClinicRepository clinicRepository;
    private EmployeeMapper employeeMapper;
    private TenantScopeResolver tenantScopeResolver;
    private EmployeeServiceImpl service;

    @BeforeEach
    void setUp() {
        employeeRepository = Mockito.mock(EmployeeRepository.class);
        clinicRepository = Mockito.mock(ClinicRepository.class);
        employeeMapper = Mockito.mock(EmployeeMapper.class);
        tenantScopeResolver = Mockito.mock(TenantScopeResolver.class);
        service = new EmployeeServiceImpl(employeeRepository, clinicRepository, employeeMapper, tenantScopeResolver);

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
    void create_withDuplicateEmailInClinic_throws() {
        Company company = Company.builder().name("Acme").build();
        company.setId(100L);

        Clinic clinic = Clinic.builder().company(company).name("Main").build();
        clinic.setId(7L);

        when(clinicRepository.findByIdAndCompanyId(7L, 100L)).thenReturn(Optional.of(clinic));
        when(employeeRepository.existsByClinicIdAndEmailIgnoreCase(7L, "john@example.com"))
                .thenReturn(true);
        when(employeeMapper.toEntity(Mockito.any())).thenReturn(new Employee());

        EmployeeCreateRequest request = new EmployeeCreateRequest(
                7L, "John", "Doe", "+905001112233",
                "john@example.com", null, EmployeeType.STAFF);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(DuplicateResourceException.class);
    }
}
