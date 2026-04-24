package com.wedent.clinic.dashboard.service;

import com.wedent.clinic.dashboard.dto.DashboardSummaryResponse;

public interface DashboardService {

    /**
     * Builds the landing-page summary.
     *
     * @param clinicId optional clinic filter. Owners can narrow to any clinic
     *                 in their company; non-owners are always clamped to
     *                 their own clinic regardless of what is passed.
     * @param doctorId optional doctor filter applied to the appointment-based
     *                 tiles (upcoming, next 7 days, today). Patient/employee
     *                 totals ignore this field — they describe the clinic as
     *                 a whole, not the single doctor.
     */
    DashboardSummaryResponse summary(Long clinicId, Long doctorId);
}
