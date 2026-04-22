package com.wedent.clinic.common.audit.repository;

import com.wedent.clinic.common.audit.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Write-heavy repository for {@link AuditLog}. Reads (if any) live in
 * forensic/export jobs outside the hot request path, so we keep the
 * interface minimal and let callers use derived queries or Specifications
 * as needs arise.
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
}
