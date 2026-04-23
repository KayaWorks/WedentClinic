package com.wedent.clinic.user.controller;

import com.wedent.clinic.common.dto.ApiResponse;
import com.wedent.clinic.user.dto.PasswordChangeRequest;
import com.wedent.clinic.user.dto.UserProfileResponse;
import com.wedent.clinic.user.dto.UserProfileUpdateRequest;
import com.wedent.clinic.user.service.UserProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Self-service endpoints for the currently-authenticated user.
 * No admin scope here — anything that touches <em>other</em> users belongs
 * under a future {@code /api/admin/users} surface.
 */
@Tag(name = "User profile")
@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService userProfileService;

    @Operation(summary = "Current authenticated user's profile (roles + permissions flattened)")
    @GetMapping
    public ResponseEntity<ApiResponse<UserProfileResponse>> getCurrent() {
        return ResponseEntity.ok(ApiResponse.ok(userProfileService.getCurrent()));
    }

    @Operation(summary = "Update the caller's profile (name fields only)")
    @PatchMapping
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateCurrent(
            @Valid @RequestBody UserProfileUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(userProfileService.updateCurrent(request)));
    }

    @Operation(summary = "Change the caller's password. Revokes every live session for the user.")
    @PostMapping("/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @Valid @RequestBody PasswordChangeRequest request,
            HttpServletRequest httpRequest) {
        userProfileService.changePassword(request, clientIp(httpRequest));
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    /**
     * Same IP-resolution policy as {@code AuthController}: honour the first
     * {@code X-Forwarded-For} hop so the audit row reflects the real client
     * IP and not the load balancer.
     */
    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return request.getRemoteAddr();
    }
}
