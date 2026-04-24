package com.wedent.clinic.preferences.controller;

import com.wedent.clinic.common.dto.ApiResponse;
import com.wedent.clinic.preferences.dto.UserPreferencesResponse;
import com.wedent.clinic.preferences.dto.UserPreferencesUpdateRequest;
import com.wedent.clinic.preferences.service.UserPreferencesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Self-service preferences for the authenticated caller. Mounted under
 * {@code /api/users/me} alongside the profile endpoints — preferences
 * conceptually belong to the user, not to the tenant, so there is no
 * admin-side path for managing someone else's prefs.
 *
 * <p>Both verbs are open to every authenticated role: the FE renders the
 * settings panel for everyone (owner, manager, doctor, staff) and each
 * caller can only ever touch their own row.</p>
 */
@Tag(name = "User preferences")
@RestController
@RequestMapping("/api/users/me/preferences")
@RequiredArgsConstructor
public class UserPreferencesController {

    private final UserPreferencesService userPreferencesService;

    @Operation(summary = "Effective preferences for the caller (defaults if no row yet)")
    @PreAuthorize("hasAnyRole('CLINIC_OWNER','MANAGER','DOCTOR','STAFF')")
    @GetMapping
    public ResponseEntity<ApiResponse<UserPreferencesResponse>> getCurrent() {
        return ResponseEntity.ok(ApiResponse.ok(userPreferencesService.getCurrent()));
    }

    @Operation(summary = "Partial update — fields omitted stay unchanged, "
            + "notifications map is merged key-by-key")
    @PreAuthorize("hasAnyRole('CLINIC_OWNER','MANAGER','DOCTOR','STAFF')")
    @PatchMapping
    public ResponseEntity<ApiResponse<UserPreferencesResponse>> patchCurrent(
            @Valid @RequestBody UserPreferencesUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(userPreferencesService.updateCurrent(request)));
    }
}
