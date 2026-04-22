package com.wedent.clinic.patient;

import com.wedent.clinic.clinic.entity.Clinic;
import com.wedent.clinic.clinic.repository.ClinicRepository;
import com.wedent.clinic.common.exception.DuplicateResourceException;
import com.wedent.clinic.common.tenant.TenantScopeResolver;
import com.wedent.clinic.company.entity.Company;
import com.wedent.clinic.patient.dto.PatientCreateRequest;
import com.wedent.clinic.patient.entity.Gender;
import com.wedent.clinic.patient.entity.Patient;
import com.wedent.clinic.patient.mapper.PatientMapper;
import com.wedent.clinic.patient.repository.PatientRepository;
import com.wedent.clinic.patient.service.impl.PatientServiceImpl;
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

class PatientServiceImplTest {

    private PatientRepository patientRepository;
    private ClinicRepository clinicRepository;
    private PatientMapper patientMapper;
    private TenantScopeResolver tenantScopeResolver;
    private PatientServiceImpl service;

    @BeforeEach
    void setUp() {
        patientRepository = Mockito.mock(PatientRepository.class);
        clinicRepository = Mockito.mock(ClinicRepository.class);
        patientMapper = Mockito.mock(PatientMapper.class);
        tenantScopeResolver = Mockito.mock(TenantScopeResolver.class);
        service = new PatientServiceImpl(patientRepository, clinicRepository, patientMapper, tenantScopeResolver);

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
    void create_withDuplicatePhone_throwsDuplicate() {
        Company company = Company.builder().name("Acme").build();
        company.setId(100L);

        Clinic clinic = Clinic.builder().name("Main").company(company).build();
        clinic.setId(7L);

        when(clinicRepository.findByIdAndCompanyId(7L, 100L)).thenReturn(Optional.of(clinic));
        when(patientRepository.existsByCompanyIdAndPhone(100L, "+905001112233")).thenReturn(true);
        when(patientMapper.toEntity(Mockito.any())).thenReturn(new Patient());

        PatientCreateRequest request = new PatientCreateRequest(
                7L, "John", "Doe", "+905001112233",
                "john@x.com", null, Gender.MALE, null);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(DuplicateResourceException.class);
    }
}
