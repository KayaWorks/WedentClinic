package com.wedent.clinic.company;

import com.wedent.clinic.common.audit.AuditEventPublisher;
import com.wedent.clinic.common.audit.event.AuditEvent;
import com.wedent.clinic.common.audit.event.AuditEventType;
import com.wedent.clinic.common.exception.BusinessException;
import com.wedent.clinic.common.exception.DuplicateResourceException;
import com.wedent.clinic.common.exception.ErrorCode;
import com.wedent.clinic.common.exception.ResourceNotFoundException;
import com.wedent.clinic.company.dto.CompanyResponse;
import com.wedent.clinic.company.dto.CompanyUpdateRequest;
import com.wedent.clinic.company.entity.Company;
import com.wedent.clinic.company.mapper.CompanyMapper;
import com.wedent.clinic.company.repository.CompanyRepository;
import com.wedent.clinic.company.service.impl.CompanyServiceImpl;
import com.wedent.clinic.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompanyServiceImplTest {

    private final CompanyRepository companyRepository = Mockito.mock(CompanyRepository.class);
    private final CompanyMapper companyMapper = Mockito.mock(CompanyMapper.class);
    private final AuditEventPublisher auditEventPublisher = Mockito.mock(AuditEventPublisher.class);
    private final CompanyServiceImpl service = new CompanyServiceImpl(
            companyRepository, companyMapper, auditEventPublisher);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    // ─── read ───────────────────────────────────────────────────────────────

    @Test
    void getCurrent_resolvesCompanyFromSecurityContext() {
        authenticate(1L, 100L, "CLINIC_OWNER");
        Company company = Company.builder().name("Acme Dental").taxNumber("1234567890").build();
        company.setId(100L);
        CompanyResponse expected = new CompanyResponse(100L, "Acme Dental", "1234567890", null, null);
        when(companyRepository.findById(100L)).thenReturn(Optional.of(company));
        when(companyMapper.toResponse(company)).thenReturn(expected);

        CompanyResponse actual = service.getCurrent();

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void getCurrent_missingRow_throwsResourceNotFound() {
        authenticate(1L, 100L, "CLINIC_OWNER");
        when(companyRepository.findById(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(service::getCurrent).isInstanceOf(ResourceNotFoundException.class);
    }

    // ─── update ─────────────────────────────────────────────────────────────

    @Test
    void updateCurrent_asNonOwner_rejectedWithAccessDenied() {
        authenticate(2L, 100L, "MANAGER");

        BusinessException ex = catchThrowableOfType(
                () -> service.updateCurrent(new CompanyUpdateRequest("New", null, null, null)),
                BusinessException.class);
        assertThat(ex).isNotNull();
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED);
        verify(companyRepository, never()).findById(any());
        verify(auditEventPublisher, never()).publish(any());
    }

    @Test
    void updateCurrent_duplicateTaxNumber_rejectedWith409() {
        authenticate(1L, 100L, "CLINIC_OWNER");
        Company company = Company.builder().name("Acme").taxNumber("OLD").build();
        company.setId(100L);
        when(companyRepository.findById(100L)).thenReturn(Optional.of(company));
        when(companyRepository.existsByTaxNumberAndIdNot("NEWTAX", 100L)).thenReturn(true);

        assertThatThrownBy(() ->
                service.updateCurrent(new CompanyUpdateRequest(null, "NEWTAX", null, null)))
                .isInstanceOf(DuplicateResourceException.class);
        verify(auditEventPublisher, never()).publish(any());
    }

    @Test
    void updateCurrent_happyPath_appliesFieldsAndAuditsDiff() {
        authenticate(1L, 100L, "CLINIC_OWNER");
        Company company = Company.builder().name("Acme").taxNumber("OLD").phone("+900000000000").build();
        company.setId(100L);
        when(companyRepository.findById(100L)).thenReturn(Optional.of(company));
        when(companyRepository.existsByTaxNumberAndIdNot("NEWTAX", 100L)).thenReturn(false);

        // Simulate mapper writing phone + email; name / taxNumber are
        // applied directly by the service before the mapper pass.
        Mockito.doAnswer(inv -> {
            CompanyUpdateRequest req = inv.getArgument(0);
            if (req.phone() != null) company.setPhone(req.phone());
            if (req.email() != null) company.setEmail(req.email());
            return null;
        }).when(companyMapper).updateEntity(any(CompanyUpdateRequest.class), any(Company.class));

        when(companyMapper.toResponse(company)).thenReturn(
                new CompanyResponse(100L, "Acme Dental", "NEWTAX", "+905550000000", null));

        service.updateCurrent(new CompanyUpdateRequest(
                "Acme Dental", "NEWTAX", "+905550000000", null));

        assertThat(company.getName()).isEqualTo("Acme Dental");
        assertThat(company.getTaxNumber()).isEqualTo("NEWTAX");
        assertThat(company.getPhone()).isEqualTo("+905550000000");

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventPublisher).publish(captor.capture());
        AuditEvent event = captor.getValue();
        assertThat(event.type()).isEqualTo(AuditEventType.COMPANY_UPDATED);
        assertThat(event.targetId()).isEqualTo(100L);
        Map<String, Object> detail = event.detail();
        assertThat(detail).containsKeys("name", "taxNumber", "phone");
        assertThat(detail).doesNotContainKey("email");
    }

    // ─────────────────────────────────────────────────────────────────────────

    private static void authenticate(Long userId, Long companyId, String role) {
        AuthenticatedUser principal = new AuthenticatedUser(
                userId, "user@example.com", companyId, null, Set.of(role), List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.authorities()));
    }
}
