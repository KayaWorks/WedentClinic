package com.wedent.clinic.clinic;

import com.wedent.clinic.clinic.dto.ClinicCreateRequest;
import com.wedent.clinic.clinic.dto.ClinicResponse;
import com.wedent.clinic.clinic.dto.ClinicUpdateRequest;
import com.wedent.clinic.clinic.entity.Clinic;
import com.wedent.clinic.clinic.mapper.ClinicMapper;
import com.wedent.clinic.clinic.repository.ClinicRepository;
import com.wedent.clinic.clinic.service.impl.ClinicServiceImpl;
import com.wedent.clinic.common.audit.AuditEventPublisher;
import com.wedent.clinic.common.audit.event.AuditEvent;
import com.wedent.clinic.common.audit.event.AuditEventType;
import com.wedent.clinic.common.exception.BusinessException;
import com.wedent.clinic.common.exception.DuplicateResourceException;
import com.wedent.clinic.common.exception.ErrorCode;
import com.wedent.clinic.common.exception.ResourceNotFoundException;
import com.wedent.clinic.common.exception.TenantScopeViolationException;
import com.wedent.clinic.company.entity.Company;
import com.wedent.clinic.company.repository.CompanyRepository;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers scope-switching reads + owner-only writes of ClinicServiceImpl.
 * Audit publishing is asserted where it matters — the UPDATE diff is the
 * most fragile piece so it gets an explicit captor.
 */
class ClinicServiceImplTest {

    private final ClinicRepository clinicRepository = Mockito.mock(ClinicRepository.class);
    private final CompanyRepository companyRepository = Mockito.mock(CompanyRepository.class);
    private final ClinicMapper clinicMapper = Mockito.mock(ClinicMapper.class);
    private final AuditEventPublisher auditEventPublisher = Mockito.mock(AuditEventPublisher.class);
    private final ClinicServiceImpl service = new ClinicServiceImpl(
            clinicRepository, companyRepository, clinicMapper, auditEventPublisher);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    // ─── reads ──────────────────────────────────────────────────────────────

    @Test
    void list_asOwner_returnsEveryClinicInCompany() {
        authenticate(1L, 100L, null, "CLINIC_OWNER");
        Clinic c1 = clinic(10L, 100L, "Kadıköy");
        Clinic c2 = clinic(11L, 100L, "Beşiktaş");
        when(clinicRepository.findAllByCompanyId(100L)).thenReturn(List.of(c1, c2));
        when(clinicMapper.toResponse(c1)).thenReturn(response(10L, 100L, "Kadıköy"));
        when(clinicMapper.toResponse(c2)).thenReturn(response(11L, 100L, "Beşiktaş"));

        List<ClinicResponse> result = service.list();

        assertThat(result).extracting(ClinicResponse::id).containsExactly(10L, 11L);
        verify(clinicRepository, never()).findByIdAndCompanyId(anyLong(), anyLong());
    }

    @Test
    void list_asNonOwner_clampedToOwnClinic() {
        authenticate(2L, 100L, 11L, "MANAGER");
        Clinic own = clinic(11L, 100L, "Beşiktaş");
        when(clinicRepository.findByIdAndCompanyId(11L, 100L)).thenReturn(Optional.of(own));
        when(clinicMapper.toResponse(own)).thenReturn(response(11L, 100L, "Beşiktaş"));

        List<ClinicResponse> result = service.list();

        assertThat(result).extracting(ClinicResponse::id).containsExactly(11L);
        verify(clinicRepository, never()).findAllByCompanyId(anyLong());
    }

    @Test
    void list_asNonOwnerWithNoClinicStamp_returnsEmpty() {
        authenticate(3L, 100L, null, "DOCTOR");

        assertThat(service.list()).isEmpty();

        verify(clinicRepository, never()).findAllByCompanyId(anyLong());
        verify(clinicRepository, never()).findByIdAndCompanyId(anyLong(), anyLong());
    }

    @Test
    void getById_missing_throwsResourceNotFound() {
        authenticate(1L, 100L, null, "CLINIC_OWNER");
        when(clinicRepository.findByIdAndCompanyId(42L, 100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(42L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getById_asNonOwnerReachingSiblingClinic_throwsTenantScopeViolation() {
        authenticate(2L, 100L, 11L, "MANAGER");
        Clinic sibling = clinic(12L, 100L, "Ataşehir");
        when(clinicRepository.findByIdAndCompanyId(12L, 100L)).thenReturn(Optional.of(sibling));

        assertThatThrownBy(() -> service.getById(12L))
                .isInstanceOf(TenantScopeViolationException.class);
        verify(clinicMapper, never()).toResponse(any());
    }

    // ─── create ─────────────────────────────────────────────────────────────

    @Test
    void create_asNonOwner_rejectedWithAccessDenied() {
        authenticate(2L, 100L, 11L, "MANAGER");

        BusinessException ex = catchThrowableOfType(
                () -> service.create(new ClinicCreateRequest("Çamlıca", null, null, null)),
                BusinessException.class);
        assertThat(ex).isNotNull();
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED);
        verify(clinicRepository, never()).save(any());
        verify(auditEventPublisher, never()).publish(any());
    }

    @Test
    void create_duplicateName_rejected() {
        authenticate(1L, 100L, null, "CLINIC_OWNER");
        when(clinicRepository.existsByCompanyIdAndNameIgnoreCase(100L, "Kadıköy")).thenReturn(true);

        assertThatThrownBy(() ->
                service.create(new ClinicCreateRequest("  Kadıköy  ", null, null, null)))
                .isInstanceOf(DuplicateResourceException.class);
        verify(clinicRepository, never()).save(any());
    }

    @Test
    void create_happyPath_savesAndPublishesAudit() {
        authenticate(1L, 100L, null, "CLINIC_OWNER");
        Company company = Company.builder().name("Acme").build();
        company.setId(100L);
        when(companyRepository.findById(100L)).thenReturn(Optional.of(company));
        when(clinicRepository.existsByCompanyIdAndNameIgnoreCase(100L, "Kadıköy")).thenReturn(false);

        Clinic entityFromMapper = Clinic.builder().build();
        when(clinicMapper.toEntity(any(ClinicCreateRequest.class))).thenReturn(entityFromMapper);

        Clinic saved = clinic(10L, 100L, "Kadıköy");
        when(clinicRepository.save(entityFromMapper)).thenReturn(saved);
        when(clinicMapper.toResponse(saved)).thenReturn(response(10L, 100L, "Kadıköy"));

        ClinicResponse result = service.create(new ClinicCreateRequest("  Kadıköy  ", null, null, null));

        assertThat(result.id()).isEqualTo(10L);
        // Name was trimmed before persisting.
        assertThat(entityFromMapper.getName()).isEqualTo("Kadıköy");
        assertThat(entityFromMapper.getCompany()).isEqualTo(company);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventPublisher).publish(captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(AuditEventType.CLINIC_CREATED);
        assertThat(captor.getValue().clinicId()).isEqualTo(10L);
        assertThat(captor.getValue().detail()).containsEntry("name", "Kadıköy");
    }

    // ─── update ─────────────────────────────────────────────────────────────

    @Test
    void update_capturesFromToDiffForChangedFields() {
        authenticate(1L, 100L, null, "CLINIC_OWNER");
        Clinic existing = clinic(10L, 100L, "Kadıköy");
        existing.setPhone("+902121111111");
        when(clinicRepository.findByIdAndCompanyId(10L, 100L)).thenReturn(Optional.of(existing));
        when(clinicMapper.toResponse(existing)).thenReturn(response(10L, 100L, "Kadıköy Merkez"));

        // Simulate the mapper writing the new phone through.
        Mockito.doAnswer(inv -> {
            ClinicUpdateRequest req = inv.getArgument(0);
            if (req.phone() != null) existing.setPhone(req.phone());
            return null;
        }).when(clinicMapper).updateEntity(any(ClinicUpdateRequest.class), any(Clinic.class));

        ClinicUpdateRequest request = new ClinicUpdateRequest(
                "Kadıköy Merkez", null, "+902122222222", null);
        service.update(10L, request);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventPublisher).publish(captor.capture());
        Map<String, Object> detail = captor.getValue().detail();
        assertThat(detail).containsKeys("name", "phone");
        assertThat(detail).doesNotContainKey("address");
        assertThat(detail).doesNotContainKey("email");
        assertThat(captor.getValue().type()).isEqualTo(AuditEventType.CLINIC_UPDATED);
    }

    @Test
    void update_blankNameRejectedAsValidation() {
        authenticate(1L, 100L, null, "CLINIC_OWNER");
        when(clinicRepository.findByIdAndCompanyId(10L, 100L)).thenReturn(Optional.of(clinic(10L, 100L, "Kadıköy")));

        BusinessException ex = catchThrowableOfType(
                () -> service.update(10L, new ClinicUpdateRequest("   ", null, null, null)),
                BusinessException.class);
        assertThat(ex).isNotNull();
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void update_duplicateNewNameRejected() {
        authenticate(1L, 100L, null, "CLINIC_OWNER");
        when(clinicRepository.findByIdAndCompanyId(10L, 100L))
                .thenReturn(Optional.of(clinic(10L, 100L, "Kadıköy")));
        when(clinicRepository.existsByCompanyIdAndNameIgnoreCaseAndIdNot(100L, "Beşiktaş", 10L))
                .thenReturn(true);

        assertThatThrownBy(() ->
                service.update(10L, new ClinicUpdateRequest("Beşiktaş", null, null, null)))
                .isInstanceOf(DuplicateResourceException.class);
    }

    // ─── delete ─────────────────────────────────────────────────────────────

    @Test
    void delete_lastActiveClinic_refusedWithBusinessRuleViolation() {
        authenticate(1L, 100L, null, "CLINIC_OWNER");
        Clinic only = clinic(10L, 100L, "Kadıköy");
        when(clinicRepository.findByIdAndCompanyId(10L, 100L)).thenReturn(Optional.of(only));
        when(clinicRepository.findAllByCompanyId(100L)).thenReturn(List.of(only));

        BusinessException ex = catchThrowableOfType(() -> service.delete(10L), BusinessException.class);
        assertThat(ex).isNotNull();
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_RULE_VIOLATION);
        verify(clinicRepository, never()).delete(any());
    }

    @Test
    void delete_happyPath_softDeletesAndPublishesAudit() {
        authenticate(1L, 100L, null, "CLINIC_OWNER");
        Clinic toDelete = clinic(10L, 100L, "Kadıköy");
        Clinic sibling = clinic(11L, 100L, "Beşiktaş");
        when(clinicRepository.findByIdAndCompanyId(10L, 100L)).thenReturn(Optional.of(toDelete));
        when(clinicRepository.findAllByCompanyId(100L)).thenReturn(List.of(toDelete, sibling));

        service.delete(10L);

        verify(clinicRepository).delete(toDelete);
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventPublisher).publish(captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(AuditEventType.CLINIC_DELETED);
        assertThat(captor.getValue().clinicId()).isEqualTo(10L);
    }

    // ─────────────────────────────────────────────────────────────────────────

    private static void authenticate(Long userId, Long companyId, Long clinicId, String role) {
        AuthenticatedUser principal = new AuthenticatedUser(
                userId, "user@example.com", companyId, clinicId, Set.of(role), List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.authorities()));
    }

    private static Clinic clinic(Long id, Long companyId, String name) {
        Company company = Company.builder().name("Acme").build();
        company.setId(companyId);
        Clinic c = Clinic.builder().name(name).company(company).build();
        c.setId(id);
        return c;
    }

    private static ClinicResponse response(Long id, Long companyId, String name) {
        return new ClinicResponse(id, companyId, name, null, null, null);
    }
}
