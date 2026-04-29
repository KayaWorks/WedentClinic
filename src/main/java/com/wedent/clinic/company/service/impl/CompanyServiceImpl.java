package com.wedent.clinic.company.service.impl;

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
import com.wedent.clinic.company.service.CompanyService;
import com.wedent.clinic.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Resolves and mutates the caller's own company. Read path is un-cached on
 * purpose — the row is tiny and reads are infrequent — and the update path
 * emits a {@code COMPANY_UPDATED} audit with a field-level from/to diff so
 * ops can see exactly what changed (taxNumber edits in particular).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CompanyServiceImpl implements CompanyService {

    private final CompanyRepository companyRepository;
    @Qualifier("companyMapperImpl")
    private final CompanyMapper companyMapper;
    private final AuditEventPublisher auditEventPublisher;

    @Override
    @Transactional(readOnly = true)
    public CompanyResponse getCurrent() {
        return companyMapper.toResponse(loadCurrent());
    }

    @Override
    public CompanyResponse updateCurrent(CompanyUpdateRequest request) {
        if (!SecurityUtils.hasRole(SecurityUtils.ROLE_CLINIC_OWNER)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED,
                    "Only the clinic owner can edit the company profile");
        }
        Company company = loadCurrent();

        String originalName      = company.getName();
        String originalTaxNumber = company.getTaxNumber();
        String originalPhone     = company.getPhone();
        String originalEmail     = company.getEmail();

        if (request.name() != null) {
            String normalized = request.name().trim();
            if (!StringUtils.hasText(normalized)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Company name cannot be blank");
            }
            company.setName(normalized);
        }
        if (request.taxNumber() != null) {
            String normalized = request.taxNumber().trim();
            if (!StringUtils.hasText(normalized)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Tax number cannot be blank");
            }
            if (!normalized.equals(originalTaxNumber)
                    && companyRepository.existsByTaxNumberAndIdNot(normalized, company.getId())) {
                // Should be rare — taxNumbers are semi-public — but let's
                // return a proper 409 rather than a DB constraint failure.
                throw new DuplicateResourceException(
                        "Tax number %s is already in use".formatted(normalized));
            }
            company.setTaxNumber(normalized);
        }
        // Contact fields: straight-through via mapper so null-skip works.
        companyMapper.updateEntity(stripStructuralFields(request), company);

        Map<String, Object> diff = new HashMap<>();
        diffField(diff, "name",      originalName,      company.getName());
        diffField(diff, "taxNumber", originalTaxNumber, company.getTaxNumber());
        diffField(diff, "phone",     originalPhone,     company.getPhone());
        diffField(diff, "email",     originalEmail,     company.getEmail());

        auditEventPublisher.publish(AuditEvent.builder(AuditEventType.COMPANY_UPDATED)
                .actorUserId(SecurityUtils.currentUserIdOrNull())
                .companyId(company.getId())
                .targetType("Company")
                .targetId(company.getId())
                .detail(diff.isEmpty() ? Map.of("noop", true) : diff)
                .build());

        return companyMapper.toResponse(company);
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    private Company loadCurrent() {
        Long companyId = SecurityUtils.currentCompanyId();
        return companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company", companyId));
    }

    /**
     * name / taxNumber were already normalised + applied directly; stripping
     * them keeps the MapStruct pass focused on the straight-through fields.
     */
    private static CompanyUpdateRequest stripStructuralFields(CompanyUpdateRequest request) {
        return new CompanyUpdateRequest(null, null, request.phone(), request.email());
    }

    private static void diffField(Map<String, Object> diff, String key, String before, String after) {
        if (!Objects.equals(before, after)) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("from", before);
            entry.put("to", after);
            diff.put(key, entry);
        }
    }
}
