package com.wedent.clinic.common.audit.repository;

import com.wedent.clinic.common.audit.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * Write-heavy repository for {@link AuditLog}. Reads (if any) live in
 * forensic/export jobs outside the hot request path, so we keep the
 * interface minimal and let callers use derived queries or Specifications
 * as needs arise.
 *
 * <p>The one exception is the dashboard feed below — it's a tightly bounded
 * read (last N events, tenant-scoped, pre-filtered by a whitelist of event
 * types) and hitting the audit table directly beats standing up a separate
 * read model.</p>
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /**
     * Recent audit rows for a tenant, filtered to a caller-supplied whitelist
     * of {@code eventType} strings. The clinic filter is strict (no NULL
     * passthrough) so a non-owner never sees events outside their clinic.
     */
    @Query("""
            SELECT a FROM AuditLog a
            WHERE a.companyId = :companyId
              AND (:clinicId IS NULL OR a.clinicId = :clinicId)
              AND a.eventType IN :types
            ORDER BY a.occurredAt DESC
            """)
    List<AuditLog> findRecentByScope(Long companyId,
                                     Long clinicId,
                                     Collection<String> types,
                                     Pageable pageable);

    /**
     * Paginated activity feed for a single patient.
     * {@code eventType} and {@code targetType} are optional whitelist filters;
     * pass {@code null} to skip.
     */
    @Query("""
            SELECT a FROM AuditLog a
            WHERE a.companyId = :companyId
              AND a.patientId = :patientId
              AND (:eventType IS NULL OR a.eventType = :eventType)
              AND (:targetType IS NULL OR a.targetType = :targetType)
            ORDER BY a.occurredAt DESC
            """)
    Page<AuditLog> findByPatient(Long companyId,
                                  Long patientId,
                                  String eventType,
                                  String targetType,
                                  Pageable pageable);
}
