package com.wedent.clinic.dashboard.service;

import com.wedent.clinic.dashboard.dto.DashboardSummaryResponse;

public interface DashboardService {

    /**
     * Builds the landing-page summary for the current authenticated user.
     * Scope is implicit — owners see company-wide totals, everyone else is
     * clamped to their own clinic.
     */
    DashboardSummaryResponse summary();
}
