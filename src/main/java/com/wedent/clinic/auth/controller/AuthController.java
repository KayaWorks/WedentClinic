package com.wedent.clinic.auth.controller;

import com.wedent.clinic.auth.dto.LoginRequest;
import com.wedent.clinic.auth.dto.LoginResponse;
import com.wedent.clinic.auth.dto.LogoutRequest;
import com.wedent.clinic.auth.dto.RefreshRequest;
import com.wedent.clinic.auth.dto.RefreshResponse;
import com.wedent.clinic.auth.service.AuthService;
import com.wedent.clinic.common.audit.AuditEventPublisher;
import com.wedent.clinic.common.audit.event.AuditEvent;
import com.wedent.clinic.common.audit.event.AuditEventType;
import com.wedent.clinic.common.dto.ApiResponse;
import com.wedent.clinic.common.exception.BusinessException;
import com.wedent.clinic.common.exception.ErrorCode;
import com.wedent.clinic.common.exception.InvalidCredentialsException;
import com.wedent.clinic.security.AuthenticatedUser;
import com.wedent.clinic.security.JwtTokenProvider;
import com.wedent.clinic.security.SecurityUtils;
import com.wedent.clinic.security.blacklist.AccessTokenBlacklist;
import com.wedent.clinic.security.ratelimit.LoginRateLimiter;
import io.jsonwebtoken.Claims;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "Authentication")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@SecurityRequirements
public class AuthController {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthService authService;
    private final LoginRateLimiter loginRateLimiter;
    private final AuditEventPublisher auditEventPublisher;
    private final JwtTokenProvider tokenProvider;
    private final AccessTokenBlacklist accessTokenBlacklist;

    @Operation(summary = "Login with email/password and receive a JWT access token + refresh token")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request,
                                                            HttpServletRequest httpRequest) {
        String ip = clientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        String key = LoginRateLimiter.keyOf(ip, request.email());
        if (loginRateLimiter.isBlocked(key)) {
            auditEventPublisher.publish(AuditEvent.builder(AuditEventType.LOGIN_RATE_LIMITED)
                    .actorEmail(request.email())
                    .ipAddress(ip)
                    .detail(Map.of("reason", "WINDOW_EXCEEDED"))
                    .build());
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS,
                    "Too many failed login attempts. Try again later.");
        }
        try {
            LoginResponse response = authService.login(request, ip, userAgent);
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

    @Operation(summary = "Exchange a valid refresh token for a new access token (rotates the refresh token)")
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<RefreshResponse>> refresh(@Valid @RequestBody RefreshRequest request,
                                                                HttpServletRequest httpRequest) {
        String ip = clientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        RefreshResponse response = authService.refresh(request.refreshToken(), ip, userAgent);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @Operation(summary = "Revoke refresh token + blacklist the presented access token")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request,
                                       HttpServletRequest httpRequest) {
        String ip = clientIp(httpRequest);
        authService.logout(request.refreshToken());

        // If the caller included an Authorization header, blacklist that
        // access token until its natural expiry so it can't keep making
        // calls with the stale session.
        String accessToken = bearerToken(httpRequest);
        if (accessToken != null) {
            tokenProvider.parse(accessToken).ifPresent(this::blacklist);
        }

        AuthenticatedUser caller = SecurityUtils.currentUserOptional().orElse(null);
        AuditEvent.Builder audit = AuditEvent.builder(AuditEventType.LOGOUT).ipAddress(ip);
        if (caller != null) {
            audit.actorUserId(caller.userId())
                 .actorEmail(caller.email())
                 .companyId(caller.companyId())
                 .clinicId(caller.clinicId());
        }
        auditEventPublisher.publish(audit.build());

        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    private void blacklist(Claims claims) {
        if (claims.getId() == null || claims.getExpiration() == null) return;
        accessTokenBlacklist.blacklist(claims.getId(), claims.getExpiration().toInstant());
    }

    private static String bearerToken(HttpServletRequest request) {
        String header = request.getHeader(AUTH_HEADER);
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
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
