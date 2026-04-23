package com.wedent.clinic.dashboard.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Composite payload for the admin shell landing page.
 *
 * <p>Shape is deliberately flat so the frontend can render each tile in a
 * single pass without re-shuffling nested structures. {@code scope} makes
 * the effective tenant scope explicit (owner-wide vs. clinic-clamped) so
 * the UI can label tiles appropriately ("Company total" vs. "Your clinic").</p>
 */
public record DashboardSummaryResponse(
        Scope scope,
        Totals totals,
        TodayBreakdown today,
        List<DailyBucket> next7Days,
        List<RecentActivity> recentActivity,
        Instant generatedAt
) {

    public record Scope(Long companyId, Long clinicId, boolean companyWide) {}

    public record Totals(
            long activePatients,
            long activeEmployees,
            long activeDoctors,
            long upcomingAppointments
    ) {}

    public record TodayBreakdown(
            LocalDate date,
            long total,
            long created,
            long confirmed,
            long completed,
            long cancelled,
            long noShow
    ) {}

    public record DailyBucket(LocalDate date, long count) {}

    /**
     * A trimmed audit row for the dashboard "recent activity" feed.
     * PHI-adjacent fields (patient name, note, etc.) are deliberately not
     * included — the frontend fetches the full row on drill-down.
     */
    public record RecentActivity(
            Long id,
            String type,
            Long actorUserId,
            String actorEmail,
            String targetType,
            Long targetId,
            Instant occurredAt
    ) {}
}
