package com.wedent.clinic.session.service.impl;

import com.wedent.clinic.auth.service.RefreshTokenService;
import com.wedent.clinic.common.audit.AuditEventPublisher;
import com.wedent.clinic.common.audit.event.AuditEvent;
import com.wedent.clinic.common.audit.event.AuditEventType;
import com.wedent.clinic.security.AuthenticatedUser;
import com.wedent.clinic.security.SecurityUtils;
import com.wedent.clinic.session.dto.SessionResponse;
import com.wedent.clinic.session.service.SessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Thin façade in front of {@link RefreshTokenService}. Adds: caller-scope
 * resolution, DTO mapping, and an audit trail entry per revoke action.
 */
@Service
@RequiredArgsConstructor
public class SessionServiceImpl implements SessionService {

    private final RefreshTokenService refreshTokenService;
    private final AuditEventPublisher auditEventPublisher;

    @Override
    public List<SessionResponse> listMine() {
        Long userId = SecurityUtils.currentUser().userId();
        return refreshTokenService.listSessions(userId).stream()
                .map(s -> new SessionResponse(
                        s.sessionId(), s.issuedAt(), s.expiresAt(),
                        s.ipAddress(), s.userAgent()))
                .toList();
    }

    @Override
    public boolean revokeMine(String sessionId) {
        AuthenticatedUser caller = SecurityUtils.currentUser();
        boolean revoked = refreshTokenService.revokeSession(caller.userId(), sessionId);
        if (revoked) {
            // Truncate the id in the audit detail so the full hash never
            // lands in the log file — it is technically derivable but we
            // treat it like a credential identifier and minimise exposure.
            auditEventPublisher.publish(AuditEvent.builder(AuditEventType.SESSION_REVOKED)
                    .actorUserId(caller.userId())
                    .actorEmail(caller.email())
                    .companyId(caller.companyId())
                    .clinicId(caller.clinicId())
                    .targetType("Session")
                    .detail(Map.of("sessionIdPrefix", shortId(sessionId)))
                    .build());
        }
        return revoked;
    }

    @Override
    public int revokeAllMine() {
        AuthenticatedUser caller = SecurityUtils.currentUser();
        int count = refreshTokenService.revokeAllForUser(caller.userId());
        if (count > 0) {
            auditEventPublisher.publish(AuditEvent.builder(AuditEventType.SESSIONS_REVOKED_ALL)
                    .actorUserId(caller.userId())
                    .actorEmail(caller.email())
                    .companyId(caller.companyId())
                    .clinicId(caller.clinicId())
                    .detail(Map.of("revokedCount", count))
                    .build());
        }
        return count;
    }

    private static String shortId(String id) {
        if (id == null) return null;
        return id.length() <= 8 ? id : id.substring(0, 8);
    }
}
