package com.wedent.clinic.clinic.service.impl;

import com.wedent.clinic.clinic.dto.ClinicCreateRequest;
import com.wedent.clinic.clinic.dto.ClinicResponse;
import com.wedent.clinic.clinic.dto.ClinicUpdateRequest;
import com.wedent.clinic.clinic.entity.Clinic;
import com.wedent.clinic.clinic.mapper.ClinicMapper;
import com.wedent.clinic.clinic.repository.ClinicRepository;
import com.wedent.clinic.clinic.service.ClinicService;
import com.wedent.clinic.common.audit.AuditEventPublisher;
import com.wedent.clinic.common.audit.event.AuditEvent;
import com.wedent.clinic.common.audit.event.AuditEventType;
import com.wedent.clinic.common.exception.BusinessException;
import com.wedent.clinic.common.exception.DuplicateResourceException;
import com.wedent.clinic.common.exception.ErrorCode;
import com.wedent.clinic.common.exception.ResourceNotFoundException;
import com.wedent.clinic.company.entity.Company;
import com.wedent.clinic.company.repository.CompanyRepository;
import com.wedent.clinic.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tenant-scoped clinic reads + owner-only writes. Every mutation publishes
 * an audit event under {@code targetType = "Clinic"} so ops can reconstruct
 * the company's topology changes over time.
 *
 * <p>Create/update/delete verify the authenticated role at service layer too
 * (not just via {@code @PreAuthorize} on the controller) so a programmatic
 * call path — e.g. a scheduled job that picked up the wrong principal — still
 * can't mutate the tenant topology.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ClinicServiceImpl implements ClinicService {

    private final ClinicRepository clinicRepository;
    private final CompanyRepository companyRepository;
    private final ClinicMapper clinicMapper;
    private final AuditEventPublisher auditEventPublisher;

    @Override
    @Transactional(readOnly = true)
    public List<ClinicResponse> list() {
        Long companyId = SecurityUtils.currentCompanyId();
        if (SecurityUtils.hasRole(SecurityUtils.ROLE_CLINIC_OWNER)) {
            return clinicRepository.findAllByCompanyId(companyId).stream()
                    .map(clinicMapper::toResponse)
                    .toList();
        }
        Long clinicId = SecurityUtils.currentClinicId().orElse(null);
        if (clinicId == null) {
            return List.of();
        }
        return clinicRepository.findByIdAndCompanyId(clinicId, companyId)
                .map(clinicMapper::toResponse)
                .map(List::of)
                .orElseGet(List::of);
    }

    @Override
    @Transactional(readOnly = true)
    public ClinicResponse getById(Long id) {
        Long companyId = SecurityUtils.currentCompanyId();
        Clinic clinic = clinicRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Clinic", id));
        SecurityUtils.verifyClinicAccess(clinic.getId());
        return clinicMapper.toResponse(clinic);
    }

    @Override
    public ClinicResponse create(ClinicCreateRequest request) {
        assertOwner();
        Long companyId = SecurityUtils.currentCompanyId();
        String normalizedName = request.name().trim();
        if (clinicRepository.existsByCompanyIdAndNameIgnoreCase(companyId, normalizedName)) {
            throw new DuplicateResourceException(
                    "A clinic named '%s' already exists in this company".formatted(normalizedName));
        }
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company", companyId));

        Clinic entity = clinicMapper.toEntity(request);
        entity.setName(normalizedName);
        entity.setCompany(company);

        Clinic saved = clinicRepository.save(entity);

        auditEventPublisher.publish(AuditEvent.builder(AuditEventType.CLINIC_CREATED)
                .actorUserId(SecurityUtils.currentUserIdOrNull())
                .companyId(companyId)
                .clinicId(saved.getId())
                .targetType("Clinic")
                .targetId(saved.getId())
                .detail(Map.of("name", saved.getName()))
                .build());

        return clinicMapper.toResponse(saved);
    }

    @Override
    public ClinicResponse update(Long id, ClinicUpdateRequest request) {
        assertOwner();
        Long companyId = SecurityUtils.currentCompanyId();
        Clinic clinic = clinicRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Clinic", id));

        // Snapshot original values before we mutate — the mapper overwrites
        // them in place, so we couldn't diff after-the-fact.
        String originalName    = clinic.getName();
        String originalAddress = clinic.getAddress();
        String originalPhone   = clinic.getPhone();
        String originalEmail   = clinic.getEmail();

        if (request.name() != null) {
            String normalized = request.name().trim();
            if (!StringUtils.hasText(normalized)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Clinic name cannot be blank");
            }
            if (!normalized.equalsIgnoreCase(originalName)
                    && clinicRepository.existsByCompanyIdAndNameIgnoreCaseAndIdNot(companyId, normalized, id)) {
                throw new DuplicateResourceException(
                        "A clinic named '%s' already exists in this company".formatted(normalized));
            }
            clinic.setName(normalized);
        }
        // Remaining fields are straight-through; mapper handles null-skip semantics.
        clinicMapper.updateEntity(stripName(request), clinic);

        Map<String, Object> diff = new HashMap<>();
        diffField(diff, "name",    originalName,    clinic.getName());
        diffField(diff, "address", originalAddress, clinic.getAddress());
        diffField(diff, "phone",   originalPhone,   clinic.getPhone());
        diffField(diff, "email",   originalEmail,   clinic.getEmail());

        auditEventPublisher.publish(AuditEvent.builder(AuditEventType.CLINIC_UPDATED)
                .actorUserId(SecurityUtils.currentUserIdOrNull())
                .companyId(companyId)
                .clinicId(clinic.getId())
                .targetType("Clinic")
                .targetId(clinic.getId())
                .detail(diff.isEmpty() ? Map.of("noop", true) : diff)
                .build());

        return clinicMapper.toResponse(clinic);
    }

    @Override
    public void delete(Long id) {
        assertOwner();
        Long companyId = SecurityUtils.currentCompanyId();
        Clinic clinic = clinicRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Clinic", id));

        // Guard against deleting the last active clinic — the tenant needs at
        // least one or every other feature (patients, appointments, employees)
        // has no valid scope. Owners can deactivate any sibling but not this.
        long remaining = clinicRepository.findAllByCompanyId(companyId).stream()
                .filter(c -> !c.getId().equals(id))
                .count();
        if (remaining == 0) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Cannot delete the last active clinic of a company");
        }

        clinicRepository.delete(clinic); // soft-delete via @SQLDelete

        auditEventPublisher.publish(AuditEvent.builder(AuditEventType.CLINIC_DELETED)
                .actorUserId(SecurityUtils.currentUserIdOrNull())
                .companyId(companyId)
                .clinicId(id)
                .targetType("Clinic")
                .targetId(id)
                .detail(Map.of("name", clinic.getName()))
                .build());
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    private static void assertOwner() {
        if (!SecurityUtils.hasRole(SecurityUtils.ROLE_CLINIC_OWNER)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED,
                    "Only the clinic owner can modify the company's clinic topology");
        }
    }

    /** Strip name so the MapStruct pass doesn't re-touch what we already normalized. */
    private static ClinicUpdateRequest stripName(ClinicUpdateRequest request) {
        return new ClinicUpdateRequest(null, request.address(), request.phone(), request.email());
    }

    private static void diffField(Map<String, Object> diff, String key, String before, String after) {
        if (!java.util.Objects.equals(before, after)) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("from", before);
            entry.put("to", after);
            diff.put(key, entry);
        }
    }
}
