package com.wedent.clinic.auth.controller;

import com.wedent.clinic.auth.dto.LoginRequest;
import com.wedent.clinic.auth.dto.LoginResponse;
import com.wedent.clinic.auth.service.AuthService;
import com.wedent.clinic.common.audit.AuditEventPublisher;
import com.wedent.clinic.common.audit.event.AuditEvent;
import com.wedent.clinic.common.audit.event.AuditEventType;
import com.wedent.clinic.common.dto.ApiResponse;
import com.wedent.clinic.common.exception.BusinessException;
import com.wedent.clinic.common.exception.ErrorCode;
import com.wedent.clinic.common.exception.InvalidCredentialsException;
import com.wedent.clinic.security.ratelimit.LoginRateLimiter;
import java.util.Map;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Authentication")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@SecurityRequirements
public class AuthController {

    private final AuthService authService;
    private final LoginRateLimiter loginRateLimiter;
    private final AuditEventPublisher auditEventPublisher;

    @Operation(summary = "Login with email/password and receive a JWT access token")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request,
                                                            HttpServletRequest httpRequest) {
        String ip = clientIp(httpRequest);
        String key = LoginRateLimiter.keyOf(ip, request.email());
        if (loginRateLimiter.isBlocked(key)) {
            // Record the rate-limit trip BEFORE throwing so the response-shaping
            // in GlobalExceptionHandler doesn't swallow the audit.
            auditEventPublisher.publish(AuditEvent.builder(AuditEventType.LOGIN_RATE_LIMITED)
                    .actorEmail(request.email())
                    .ipAddress(ip)
                    .detail(Map.of("reason", "WINDOW_EXCEEDED"))
                    .build());
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS,
                    "Too many failed login attempts. Try again later.");
        }
        try {
            LoginResponse response = authService.login(request);
            loginRateLimiter.onSuccess(key);
            auditEventPublisher.publish(AuditEvent.builder(AuditEventType.LOGIN_SUCCESS)
                    .actorUserId(response.userId())
                    .actorEmail(response.email())
                    .companyId(response.companyId())
                    .clinicId(response.clinicId())
                    .ipAddress(ip)
                    .build());
            return ResponseEntity.ok(ApiResponse.ok(response));
        } catch (InvalidCredentialsException e) {
            loginRateLimiter.onFailure(key);
            auditEventPublisher.publish(AuditEvent.builder(AuditEventType.LOGIN_FAILURE)
                    .actorEmail(request.email())
                    .ipAddress(ip)
                    .detail(Map.of("reason", "INVALID_CREDENTIALS"))
                    .build());
            throw e;
        }
    }

    /**
     * Uses the first value in {@code X-Forwarded-For} when present (set by the
     * load balancer) so the real client IP drives the rate limit key, not the
     * LB's own address. Falls back to the socket peer.
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
