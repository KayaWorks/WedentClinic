package com.wedent.clinic.common.audit.service;

import com.wedent.clinic.common.audit.dto.AuditLogDto;
import com.wedent.clinic.common.dto.PageResponse;
import org.springframework.data.domain.Pageable;

public interface AuditLogService {

    /**
     * Returns a paginated activity feed for a single patient.
     * Tenant isolation is enforced via {@code companyId} from the security context.
     *
     * @param patientId  the patient to fetch activity for
     * @param eventType  optional event type filter (e.g. "APPOINTMENT_CREATED")
     * @param targetType optional target entity type filter (e.g. "Appointment")
     * @param pageable   page + sort
     */
    PageResponse<AuditLogDto> listForPatient(Long patientId,
                                              String eventType,
                                              String targetType,
                                              Pageable pageable);
}
