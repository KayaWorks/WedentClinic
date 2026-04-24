package com.wedent.clinic.preferences.entity;

import com.wedent.clinic.common.entity.BaseEntity;
import com.wedent.clinic.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Per-user preferences row. Created lazily — the service synthesises a
 * defaults-only record when no row exists for a user, so existing tenants
 * don't need a backfill insert when this feature ships.
 *
 * <p>{@code notifications} is a JSONB blob keyed by event name (e.g.
 * {@code appointment_reminder}, {@code login_alert}) → boolean opt-in. New
 * events can be added without a migration; the service merges incoming maps
 * into the stored one so the FE only sends deltas.</p>
 */
@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "user_preferences")
public class UserPreferences extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true, updatable = false)
    private User user;

    @Column(name = "language", nullable = false, length = 8)
    private String language;

    @Column(name = "timezone", nullable = false, length = 64)
    private String timezone;

    @Column(name = "date_format", nullable = false, length = 20)
    private String dateFormat;

    @Column(name = "time_format", nullable = false, length = 8)
    private String timeFormat;

    @Column(name = "currency", nullable = false, length = 8)
    private String currency;

    @Column(name = "notify_email", nullable = false)
    private boolean notifyEmail;

    @Column(name = "notify_sms", nullable = false)
    private boolean notifySms;

    @Column(name = "notify_in_app", nullable = false)
    private boolean notifyInApp;

    /**
     * Pre-serialized JSON. Same approach as {@code AuditLog.detail}: keep a
     * String here so H2 (used in the legacy {@code test} profile) can still
     * boot without extra type registrars; PostgreSQLDialect maps
     * {@code SqlTypes.JSON} to JSONB on the real DB.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "notifications", nullable = false)
    private String notifications;
}
