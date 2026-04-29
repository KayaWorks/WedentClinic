package com.wedent.clinic.file.service.impl;

import com.wedent.clinic.common.audit.AuditEventPublisher;
import com.wedent.clinic.common.audit.event.AuditEvent;
import com.wedent.clinic.common.audit.event.AuditEventType;
import com.wedent.clinic.common.exception.BusinessException;
import com.wedent.clinic.common.exception.ErrorCode;
import com.wedent.clinic.common.exception.ResourceNotFoundException;
import com.wedent.clinic.file.dto.PatientFileResponse;
import com.wedent.clinic.file.entity.PatientFile;
import com.wedent.clinic.file.entity.PatientFileCategory;
import com.wedent.clinic.file.repository.PatientFileRepository;
import com.wedent.clinic.file.service.PatientFileService;
import com.wedent.clinic.patient.entity.Patient;
import com.wedent.clinic.patient.repository.PatientRepository;
import com.wedent.clinic.security.SecurityUtils;
import com.wedent.clinic.user.entity.User;
import com.wedent.clinic.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class PatientFileServiceImpl implements PatientFileService {

    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024; // 10 MB default

    private final PatientFileRepository fileRepository;
    private final PatientRepository patientRepository;
    private final UserRepository userRepository;
    private final AuditEventPublisher auditEventPublisher;

    @Override
    public PatientFileResponse upload(Long patientId, MultipartFile file,
                                      PatientFileCategory category, String description) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Yüklenecek dosya boş olamaz");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Dosya boyutu 10 MB sınırını aşıyor");
        }

        Long companyId = SecurityUtils.currentCompanyId();
        Patient patient = loadPatient(patientId, companyId);
        SecurityUtils.verifyClinicAccess(patient.getClinic().getId());

        Long userId = SecurityUtils.currentUser().userId();
        User uploader = userRepository.findByIdAndCompanyId(userId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        String originalName = file.getOriginalFilename();
        String safeName = (originalName != null && !originalName.isBlank())
                ? originalName.replaceAll("[^a-zA-Z0-9._\\-]", "_")
                : "file";

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Dosya okunamadı: " + e.getMessage());
        }

        PatientFile entity = PatientFile.builder()
                .company(patient.getCompany())
                .clinic(patient.getClinic())
                .patient(patient)
                .uploadedBy(uploader)
                .category(category != null ? category : PatientFileCategory.OTHER)
                .fileName(safeName)
                .mimeType(file.getContentType() != null ? file.getContentType() : "application/octet-stream")
                .fileSizeBytes(file.getSize())
                .description(description)
                .content(bytes)
                .build();

        PatientFile saved = fileRepository.save(entity);

        auditEventPublisher.publish(AuditEvent.builder(AuditEventType.PATIENT_FILE_UPLOADED)
                .actorUserId(userId)
                .companyId(companyId)
                .clinicId(patient.getClinic().getId())
                .patientId(patientId)
                .targetType("PatientFile")
                .targetId(saved.getId())
                .detail(Map.of(
                        "patientId", patientId,
                        "fileName", safeName,
                        "category", entity.getCategory().name(),
                        "fileSizeBytes", file.getSize()))
                .build());

        return new PatientFileResponse(
                saved.getId(),
                patient.getId(),
                patient.getClinic().getId(),
                companyId,
                userId,
                saved.getCategory(),
                saved.getFileName(),
                saved.getMimeType(),
                saved.getFileSizeBytes(),
                saved.getDescription(),
                saved.getCreatedAt());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PatientFileResponse> listForPatient(Long patientId) {
        Long companyId = SecurityUtils.currentCompanyId();
        Patient patient = loadPatient(patientId, companyId);
        SecurityUtils.verifyClinicAccess(patient.getClinic().getId());

        return fileRepository.findMetaByPatientIdAndCompanyId(patientId, companyId);
    }

    @Override
    @Transactional(readOnly = true)
    public DownloadResult download(Long fileId) {
        Long companyId = SecurityUtils.currentCompanyId();

        PatientFileResponse meta = fileRepository.findMetaByIdAndCompanyId(fileId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("PatientFile", fileId));

        SecurityUtils.verifyClinicAccess(meta.clinicId());

        byte[] content = fileRepository.findContentByIdAndCompanyId(fileId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("PatientFile", fileId));

        auditEventPublisher.publish(AuditEvent.builder(AuditEventType.PATIENT_FILE_DOWNLOADED)
                .actorUserId(SecurityUtils.currentUserIdOrNull())
                .companyId(companyId)
                .clinicId(meta.clinicId())
                .patientId(meta.patientId())
                .targetType("PatientFile")
                .targetId(fileId)
                .detail(Map.of("patientId", meta.patientId(), "fileName", meta.fileName()))
                .build());

        return new DownloadResult(meta, content);
    }

    @Override
    public void delete(Long fileId) {
        Long companyId = SecurityUtils.currentCompanyId();

        PatientFileResponse meta = fileRepository.findMetaByIdAndCompanyId(fileId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("PatientFile", fileId));

        SecurityUtils.verifyClinicAccess(meta.clinicId());

        // Load entity to trigger @SQLDelete soft-delete SQL
        PatientFile entity = fileRepository.findById(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("PatientFile", fileId));
        fileRepository.delete(entity);

        auditEventPublisher.publish(AuditEvent.builder(AuditEventType.PATIENT_FILE_DELETED)
                .actorUserId(SecurityUtils.currentUserIdOrNull())
                .companyId(companyId)
                .clinicId(meta.clinicId())
                .patientId(meta.patientId())
                .targetType("PatientFile")
                .targetId(fileId)
                .detail(Map.of("patientId", meta.patientId(), "fileName", meta.fileName()))
                .build());
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    private Patient loadPatient(Long patientId, Long companyId) {
        return patientRepository.findByIdAndCompanyId(patientId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", patientId));
    }
}
