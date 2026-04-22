package com.wedent.clinic.common.audit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Append-only security audit row.
 *
 * <p>Deliberately <b>does not</b> extend {@code BaseEntity}: audit rows must
 * never be mutated or soft-deleted, and their lifetime must not be coupled to
 * user/company/clinic rows (those are soft-deletable, but the audit trail
 * needs to outlive them for compliance/forensics).</p>
 *
 * <p>The {@code detail} column is a Postgres {@code JSONB} blob so we can
 * enrich events ad-hoc (e.g. {@code {"reason":"BAD_PASSWORD"}},
 * {@code {"from":"CREATED","to":"COMPLETED"}}) without schema churn. We store
 * the already-serialized JSON string so the DB layer isn't coupled to Jackson.</p>
 */
@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "event_type", nullable = false, length = 64, updatable = false)
    private String eventType;

    @Column(name = "actor_user_id", updatable = false)
    private Long actorUserId;

    @Column(name = "actor_email", length = 200, updatable = false)
    private String actorEmail;

    @Column(name = "company_id", updatable = false)
    private Long companyId;

    @Column(name = "clinic_id", updatable = false)
    private Long clinicId;

    @Column(name = "target_type", length = 64, updatable = false)
    private String targetType;

    @Column(name = "target_id", updatable = false)
    private Long targetId;

    /**
     * Pre-serialized JSON string. Hibernate 6 maps {@code SqlTypes.JSON} to the
     * Postgres {@code JSONB} type transparently, so we still gain index/search
     * capabilities on the DB side without the extra dependency baggage of
     * hypersistence-utils.
     */
    // NOTE: we deliberately don't set columnDefinition="jsonb" here so H2 (used
    // by the legacy `test` profile) can still create the table from entities.
    // The real Postgres column is already JSONB via V7 migration, and
    // PostgreSQLDialect maps SqlTypes.JSON → jsonb on validation.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detail", updatable = false)
    private String detail;

    @Column(name = "ip_address", length = 64, updatable = false)
    private String ipAddress;

    @Column(name = "trace_id", length = 128, updatable = false)
    private String traceId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;
}
