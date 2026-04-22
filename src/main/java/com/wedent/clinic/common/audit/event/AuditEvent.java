package com.wedent.clinic.common.audit.event;

import java.time.Instant;
import java.util.Map;

/**
 * Immutable DTO carried through {@link org.springframework.context.ApplicationEventPublisher}
 * to the async writer.
 *
 * <p>Every field is optional except {@link #type()} and {@link #occurredAt()}
 * because the event sites span authenticated/unauthenticated, tenant-scoped
 * and cross-tenant flows; the writer is responsible for treating missing
 * values as {@code NULL} columns rather than rejecting the event.</p>
 *
 * <p>{@link #detail()} is a free-form structured payload (serialized to JSONB
 * in the DB). Prefer keys that are stable and low-cardinality (e.g.
 * {@code reason}, {@code from}, {@code to}) so aggregation queries remain
 * cheap.</p>
 */
public record AuditEvent(
        AuditEventType type,
        Long actorUserId,
        String actorEmail,
        Long companyId,
        Long clinicId,
        String targetType,
        Long targetId,
        Map<String, Object> detail,
        String ipAddress,
        String traceId,
        Instant occurredAt
) {

    public static Builder builder(AuditEventType type) {
        return new Builder(type);
    }

    /**
     * Mutable builder so call sites can populate only the fields they know
     * about (e.g. a failed login has no {@code actorUserId}).
     */
    public static final class Builder {
        private final AuditEventType type;
        private Long actorUserId;
        private String actorEmail;
        private Long companyId;
        private Long clinicId;
        private String targetType;
        private Long targetId;
        private Map<String, Object> detail;
        private String ipAddress;
        private String traceId;

        private Builder(AuditEventType type) {
            this.type = type;
        }

        public Builder actorUserId(Long v)  { this.actorUserId = v; return this; }
        public Builder actorEmail(String v) { this.actorEmail = v; return this; }
        public Builder companyId(Long v)    { this.companyId = v; return this; }
        public Builder clinicId(Long v)     { this.clinicId = v; return this; }
        public Builder targetType(String v) { this.targetType = v; return this; }
        public Builder targetId(Long v)     { this.targetId = v; return this; }
        public Builder detail(Map<String, Object> v) { this.detail = v; return this; }
        public Builder ipAddress(String v)  { this.ipAddress = v; return this; }
        public Builder traceId(String v)    { this.traceId = v; return this; }

        public AuditEvent build() {
            return new AuditEvent(
                    type, actorUserId, actorEmail, companyId, clinicId,
                    targetType, targetId, detail, ipAddress, traceId,
                    Instant.now()
            );
        }
    }
}
