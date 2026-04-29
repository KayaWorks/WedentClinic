package com.wedent.clinic.patient.service.impl;

import com.wedent.clinic.clinic.entity.Clinic;
import com.wedent.clinic.clinic.repository.ClinicRepository;
import com.wedent.clinic.common.dto.PageResponse;
import com.wedent.clinic.common.exception.DuplicateResourceException;
import com.wedent.clinic.common.exception.ResourceNotFoundException;
import com.wedent.clinic.common.tenant.TenantScopeResolver;
import com.wedent.clinic.patient.dto.PatientCreateRequest;
import com.wedent.clinic.patient.dto.PatientResponse;
import com.wedent.clinic.patient.dto.PatientSummaryResponse;
import com.wedent.clinic.patient.dto.PatientUpdateRequest;
import com.wedent.clinic.patient.entity.Patient;
import com.wedent.clinic.patient.mapper.PatientMapper;
import com.wedent.clinic.patient.repository.PatientRepository;
import com.wedent.clinic.patient.service.PatientService;
import com.wedent.clinic.payment.entity.PaymentStatus;
import com.wedent.clinic.payment.repository.PaymentRepository;
import com.wedent.clinic.security.SecurityUtils;
import com.wedent.clinic.treatment.entity.TreatmentStatus;
import com.wedent.clinic.treatment.repository.TreatmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Transactional
public class PatientServiceImpl implements PatientService {

    private final PatientRepository patientRepository;
    private final ClinicRepository clinicRepository;
    private final TreatmentRepository treatmentRepository;
    private final PaymentRepository paymentRepository;
    @Qualifier("patientMapperImpl")
    private final PatientMapper patientMapper;
    private final TenantScopeResolver tenantScopeResolver;

    @Override
    public PatientResponse create(PatientCreateRequest request) {
        Long companyId = SecurityUtils.currentCompanyId();
        Clinic clinic = loadClinicForCompany(request.clinicId(), companyId);
        SecurityUtils.verifyClinicAccess(clinic.getId());

        if (patientRepository.existsByCompanyIdAndPhone(companyId, request.phone())) {
            throw new DuplicateResourceException(
                    "A patient with phone %s already exists in this company".formatted(request.phone()));
        }

        Patient entity = patientMapper.toEntity(request);
        entity.setCompany(clinic.getCompany());
        entity.setClinic(clinic);

        Patient saved = patientRepository.save(entity);
        return patientMapper.toResponse(saved);
    }

    @Override
    public PatientResponse update(Long id, PatientUpdateRequest request) {
        Patient patient = loadInScope(id);
        SecurityUtils.verifyClinicAccess(patient.getClinic().getId());

        if (!patient.getPhone().equals(request.phone()) &&
                patientRepository.existsByCompanyIdAndPhoneAndIdNot(patient.getCompany().getId(), request.phone(), id)) {
            throw new DuplicateResourceException(
                    "Phone %s already belongs to another patient".formatted(request.phone()));
        }

        patientMapper.updateEntity(request, patient);
        return patientMapper.toResponse(patient);
    }

    @Override
    @Transactional(readOnly = true)
    public PatientResponse getById(Long id) {
        Patient patient = loadInScope(id);
        SecurityUtils.verifyClinicAccess(patient.getClinic().getId());
        return patientMapper.toResponse(patient);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PatientResponse> search(Long clinicId, String name, String phone, Pageable pageable) {
        Long companyId = SecurityUtils.currentCompanyId();
        Long effectiveClinicId = tenantScopeResolver.resolveClinicScope(clinicId);
        Page<PatientResponse> page = patientRepository
                .search(companyId,
                        effectiveClinicId,
                        StringUtils.hasText(name) ? name.trim() : null,
                        StringUtils.hasText(phone) ? phone.trim() : null,
                        pageable)
                .map(patientMapper::toResponse);
        return PageResponse.of(page);
    }

    @Override
    @Transactional(readOnly = true)
    public long count(Long clinicId) {
        Long companyId = SecurityUtils.currentCompanyId();
        Long effectiveClinicId = tenantScopeResolver.resolveClinicScope(clinicId);
        return patientRepository.countByScope(companyId, effectiveClinicId);
    }

    @Override
    public void delete(Long id) {
        Patient patient = loadInScope(id);
        SecurityUtils.verifyClinicAccess(patient.getClinic().getId());
        patientRepository.delete(patient);
    }

    @Override
    @Transactional(readOnly = true)
    public PatientSummaryResponse getSummary(Long id) {
        Patient patient = loadInScope(id);
        SecurityUtils.verifyClinicAccess(patient.getClinic().getId());
        Long companyId = patient.getCompany().getId();

        long planned  = treatmentRepository.countByPatientIdAndCompanyIdAndStatus(id, companyId, TreatmentStatus.PLANNED);
        long completed = treatmentRepository.countByPatientIdAndCompanyIdAndStatus(id, companyId, TreatmentStatus.COMPLETED);
        long cancelled = treatmentRepository.countByPatientIdAndCompanyIdAndStatus(id, companyId, TreatmentStatus.CANCELLED);

        BigDecimal treatmentAmount = treatmentRepository
                .sumFeeByPatientIdAndCompanyIdAndStatus(id, companyId, TreatmentStatus.COMPLETED);
        if (treatmentAmount == null) treatmentAmount = BigDecimal.ZERO;

        BigDecimal paidAmount = paymentRepository
                .sumByPatientIdAndCompanyIdAndStatus(id, companyId, PaymentStatus.COMPLETED);
        if (paidAmount == null) paidAmount = BigDecimal.ZERO;

        BigDecimal balance = paidAmount.subtract(treatmentAmount);

        return new PatientSummaryResponse(id, planned, completed, cancelled,
                treatmentAmount, paidAmount, balance, "TRY");
    }

    private Patient loadInScope(Long id) {
        Long companyId = SecurityUtils.currentCompanyId();
        return patientRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", id));
    }

    private Clinic loadClinicForCompany(Long clinicId, Long companyId) {
        return clinicRepository.findByIdAndCompanyId(clinicId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Clinic", clinicId));
    }
}
