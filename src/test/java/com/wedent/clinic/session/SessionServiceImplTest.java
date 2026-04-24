package com.wedent.clinic.session;

import com.wedent.clinic.auth.service.RefreshTokenService;
import com.wedent.clinic.common.audit.AuditEventPublisher;
import com.wedent.clinic.common.audit.event.AuditEvent;
import com.wedent.clinic.common.audit.event.AuditEventType;
import com.wedent.clinic.security.AuthenticatedUser;
import com.wedent.clinic.session.dto.SessionResponse;
import com.wedent.clinic.session.service.impl.SessionServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionServiceImplTest {

    private final RefreshTokenService refreshTokenService = Mockito.mock(RefreshTokenService.class);
    private final AuditEventPublisher auditEventPublisher = Mockito.mock(AuditEventPublisher.class);
    private final SessionServiceImpl service = new SessionServiceImpl(
            refreshTokenService, auditEventPublisher);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listMine_mapsViewsToResponseDtos_inOrder() {
        authenticate(7L);
        Instant now = Instant.now();
        when(refreshTokenService.listSessions(7L)).thenReturn(List.of(
                new RefreshTokenService.SessionView(
                        "hashA", now, now.plusSeconds(3600), "10.0.0.1", "Chrome"),
                new RefreshTokenService.SessionView(
                        "hashB", now.minusSeconds(60), now.plusSeconds(1800), "10.0.0.2", "iOS")
        ));

        List<SessionResponse> result = service.listMine();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).sessionId()).isEqualTo("hashA");
        assertThat(result.get(0).userAgent()).isEqualTo("Chrome");
        assertThat(result.get(1).sessionId()).isEqualTo("hashB");
    }

    @Test
    void revokeMine_revoked_publishesAuditWithTruncatedId() {
        authenticate(7L);
        when(refreshTokenService.revokeSession(7L, "abcdef0123456789longhash"))
                .thenReturn(true);

        boolean result = service.revokeMine("abcdef0123456789longhash");

        assertThat(result).isTrue();
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventPublisher).publish(captor.capture());
        AuditEvent event = captor.getValue();
        assertThat(event.type()).isEqualTo(AuditEventType.SESSION_REVOKED);
        assertThat(event.actorUserId()).isEqualTo(7L);
        // Audit detail must carry only the prefix — full hash never logged.
        assertThat(event.detail())
                .containsEntry("sessionIdPrefix", "abcdef01");
    }

    @Test
    void revokeMine_notRevoked_doesNotEmitAudit() {
        authenticate(7L);
        when(refreshTokenService.revokeSession(anyLong(), anyString())).thenReturn(false);

        boolean result = service.revokeMine("unknown-id");

        assertThat(result).isFalse();
        verify(auditEventPublisher, never()).publish(any());
    }

    @Test
    void revokeAllMine_positiveCount_publishesAuditWithCount() {
        authenticate(7L);
        when(refreshTokenService.revokeAllForUser(7L)).thenReturn(3);

        int count = service.revokeAllMine();

        assertThat(count).isEqualTo(3);
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventPublisher).publish(captor.capture());
        AuditEvent event = captor.getValue();
        assertThat(event.type()).isEqualTo(AuditEventType.SESSIONS_REVOKED_ALL);
        assertThat(event.detail()).containsEntry("revokedCount", 3);
    }

    @Test
    void revokeAllMine_zeroCount_skipsAuditEntirely() {
        authenticate(7L);
        when(refreshTokenService.revokeAllForUser(7L)).thenReturn(0);

        int count = service.revokeAllMine();

        assertThat(count).isZero();
        verify(auditEventPublisher, never()).publish(any());
    }

    // ─────────────────────────────────────────────────────────────────────────

    private static void authenticate(Long userId) {
        AuthenticatedUser principal = new AuthenticatedUser(
                userId, "user@example.com", 100L, null, Set.of("DOCTOR"), List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.authorities()));
    }
}
