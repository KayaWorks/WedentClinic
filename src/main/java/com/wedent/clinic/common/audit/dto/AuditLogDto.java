package com.wedent.clinic.common.audit.dto;

import java.time.Instant;

/**
 * Lightweight projection of {@link com.wedent.clinic.common.audit.entity.AuditLog}
 * returned by the per-patient activity feed.
 *
 * <p>We intentionally omit {@code actorEmail}, {@code ipAddress}, and
 * {@code traceId} from the public DTO — those are sensitive fields only
 * needed by security/forensics tooling, not the patient-facing timeline.</p>
 */
public record AuditLogDto(
        Long id,
        String eventType,
        Long actorUserId,
        Long patientId,
        String targetType,
        Long targetId,
        String detail,
        Instant occurredAt
) {}
