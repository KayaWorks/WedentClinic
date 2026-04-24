package com.wedent.clinic.clinic.service;

import com.wedent.clinic.clinic.dto.ClinicCreateRequest;
import com.wedent.clinic.clinic.dto.ClinicResponse;
import com.wedent.clinic.clinic.dto.ClinicUpdateRequest;

import java.util.List;

/**
 * Reads + owner-only writes for the clinics under the caller's company.
 *
 * <p>Read rules:
 * <ul>
 *   <li>Owner: every clinic in the company.</li>
 *   <li>Non-owner: clamped to the single clinic stamped on their session.</li>
 * </ul>
 * Writes (create / update / delete) are owner-only and enforced at the
 * controller via {@code @PreAuthorize}; the service still double-checks the
 * tenant scope so a direct service call can't bypass it.</p>
 */
public interface ClinicService {

    List<ClinicResponse> list();

    ClinicResponse getById(Long id);

    ClinicResponse create(ClinicCreateRequest request);

    ClinicResponse update(Long id, ClinicUpdateRequest request);

    void delete(Long id);
}
