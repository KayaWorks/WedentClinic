package com.wedent.clinic.auth;

import com.wedent.clinic.auth.service.RefreshTokenService;
import com.wedent.clinic.auth.service.impl.RefreshTokenServiceImpl;
import com.wedent.clinic.common.audit.AuditEventPublisher;
import com.wedent.clinic.common.exception.InvalidCredentialsException;
import com.wedent.clinic.company.entity.Company;
import com.wedent.clinic.config.CacheProperties;
import com.wedent.clinic.security.JwtProperties;
import com.wedent.clinic.user.entity.User;
import com.wedent.clinic.user.entity.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the Redis-only refresh-token implementation end-to-end without a
 * live container.  A small in-memory stand-in for the Redis KV + SET ops
 * is plugged into a {@link StringRedisTemplate} mock, which is plenty to
 * exercise:
 * <ul>
 *   <li>Hash-only persistence (raw value never lands in the "store").</li>
 *   <li>Rotation keeps the old record around with {@code revokedAt} set,
 *       so replay detection can still fire inside the window.</li>
 *   <li>Replay triggers a user-wide revoke and an audit event.</li>
 * </ul>
 */
class RefreshTokenServiceImplTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOps;
    private SetOperations<String, String> setOps;
    private AuditEventPublisher auditEventPublisher;
    private RefreshTokenServiceImpl service;

    private final Map<String, String> kv = new HashMap<>();
    private final Map<String, Duration> ttls = new HashMap<>();
    private final Map<String, Set<String>> sets = new HashMap<>();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = Mockito.mock(StringRedisTemplate.class);
        valueOps = Mockito.mock(ValueOperations.class);
        setOps = Mockito.mock(SetOperations.class);
        auditEventPublisher = Mockito.mock(AuditEventPublisher.class);

        when(redis.opsForValue()).thenReturn(valueOps);
        when(redis.opsForSet()).thenReturn(setOps);

        // ─── VALUE ops ───────────────────────────────────────────────────────────
        Mockito.doAnswer(inv -> {
            kv.put(inv.getArgument(0), inv.getArgument(1));
            ttls.put(inv.getArgument(0), inv.getArgument(2));
            return null;
        }).when(valueOps).set(anyString(), anyString(), any(Duration.class));

        when(valueOps.get(anyString())).thenAnswer(inv -> kv.get((String) inv.getArgument(0)));

        when(redis.getExpire(anyString(), any(TimeUnit.class))).thenAnswer(inv -> {
            Duration d = ttls.getOrDefault((String) inv.getArgument(0), Duration.ZERO);
            TimeUnit unit = inv.getArgument(1);
            return unit.convert(d.toMillis(), TimeUnit.MILLISECONDS);
        });

        when(redis.delete(anyString())).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            Object removed = kv.remove(key);
            Object removedSet = sets.remove(key);
            ttls.remove(key);
            return removed != null || removedSet != null;
        });
        when(redis.expire(anyString(), any(Duration.class))).thenAnswer(inv -> {
            ttls.put(inv.getArgument(0), inv.getArgument(1));
            return true;
        });

        // ─── SET ops ─────────────────────────────────────────────────────────────
        when(setOps.add(anyString(), any(String[].class))).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            Set<String> s = sets.computeIfAbsent(key, k -> new HashSet<>());
            long added = 0;
            for (int i = 1; i < inv.getArguments().length; i++) {
                Object arg = inv.getArguments()[i];
                if (arg instanceof String[] members) {
                    for (String m : members) if (s.add(m)) added++;
                } else if (arg instanceof String member && s.add(member)) {
                    added++;
                }
            }
            return added;
        });
        when(setOps.members(anyString())).thenAnswer(inv ->
                sets.getOrDefault((String) inv.getArgument(0), Set.of()));
        when(setOps.remove(anyString(), any(Object[].class))).thenAnswer(inv -> {
            Set<String> s = sets.get((String) inv.getArgument(0));
            if (s == null) return 0L;
            long removed = 0;
            for (int i = 1; i < inv.getArguments().length; i++) {
                Object arg = inv.getArguments()[i];
                if (arg instanceof Object[] members) {
                    for (Object m : members) if (s.remove(m)) removed++;
                } else if (s.remove(arg)) {
                    removed++;
                }
            }
            return removed;
        });

        CacheProperties cacheProps = new CacheProperties(Duration.ofMinutes(10), "wedent:");
        JwtProperties jwtProps = new JwtProperties(
                new JwtProperties.Jwt(
                        "test-secret-test-secret-test-secret-test-secret-1234567890",
                        60, 14L, "wedent-clinic-test"),
                List.of());

        service = new RefreshTokenServiceImpl(redis, auditEventPublisher, jwtProps, cacheProps);
    }

    @Test
    void issue_persistsOnlyHashedToken_andTrackedInUserSet() {
        User user = activeUser(50L);

        RefreshTokenService.Issued issued = service.issue(user, "10.0.0.1", "JUnit/5");

        assertThat(issued.rawToken()).isNotBlank();
        // The raw token must not appear anywhere in the KV store — only its hash.
        kv.values().forEach(v -> assertThat(v).doesNotContain(issued.rawToken()));
        // And a record keyed by wedent:refresh:t:<hash> must exist.
        assertThat(kv.keySet()).anyMatch(k -> k.startsWith("wedent:refresh:t:"));
        // Membership list tracks the new hash for logout-all.
        assertThat(sets.get("wedent:refresh:u:50")).hasSize(1);
    }

    @Test
    void rotate_happyPath_revokesOld_issuesNew_preservesTtl() {
        User user = activeUser(50L);

        RefreshTokenService.Issued first = service.issue(user, "10.0.0.1", "ua");
        int kvSizeAfterIssue = kv.size();

        RefreshTokenService.Rotated rotated = service.rotate(first.rawToken(), "10.0.0.1", "ua");

        assertThat(rotated.newRawToken()).isNotBlank().isNotEqualTo(first.rawToken());
        assertThat(rotated.userId()).isEqualTo(50L);
        // Old record is preserved (with revokedAt) so replay can still fire.
        assertThat(kv).hasSize(kvSizeAfterIssue + 1);
    }

    @Test
    void rotate_unknownToken_throwsInvalidCredentials() {
        assertThatThrownBy(() -> service.rotate("bogus", "10.0.0.1", "ua"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void rotate_alreadyRotatedToken_triggersReplayDefense() {
        User user = activeUser(50L);
        RefreshTokenService.Issued first = service.issue(user, "10.0.0.1", "ua");
        service.rotate(first.rawToken(), "10.0.0.1", "ua"); // legitimate rotation

        // Re-present the already-rotated token → must revoke user's sessions + publish replay audit.
        assertThatThrownBy(() -> service.rotate(first.rawToken(), "10.0.0.1", "ua"))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(auditEventPublisher).publish(
                org.mockito.ArgumentMatchers.argThat(e ->
                        e.type() == com.wedent.clinic.common.audit.event.AuditEventType.TOKEN_REFRESH_REPLAY));
    }

    @Test
    void revoke_knownToken_setsRevokedAt_butKeepsRecordUntilTtl() {
        User user = activeUser(50L);
        RefreshTokenService.Issued first = service.issue(user, "10.0.0.1", "ua");
        int sizeBefore = kv.size();

        service.revoke(first.rawToken());

        // Record is still there — we only mutate the revokedAt flag in-place.
        assertThat(kv).hasSize(sizeBefore);
        String payload = kv.values().iterator().next();
        assertThat(payload).contains("\"revokedAt\"");
    }

    @Test
    void revoke_unknownOrBlankToken_isSilentNoop() {
        service.revoke(null);
        service.revoke("");
        service.revoke("   ");
        service.revoke("does-not-exist");
        // No state changes, no exception.
        assertThat(kv).isEmpty();
    }

    @Test
    void listSessions_returnsOnlyLiveOnes_newestFirst() throws InterruptedException {
        User user = activeUser(50L);
        service.issue(user, "10.0.0.1", "device-A");
        Thread.sleep(5); // make issuedAt strictly monotonic for the ordering assertion
        RefreshTokenService.Issued second = service.issue(user, "10.0.0.2", "device-B");
        Thread.sleep(5);
        service.issue(user, "10.0.0.3", "device-C");

        // Revoke the middle one — it should drop out of the live list.
        service.revoke(second.rawToken());

        List<RefreshTokenService.SessionView> live = service.listSessions(50L);

        assertThat(live).hasSize(2);
        // Newest first.
        assertThat(live.get(0).userAgent()).isEqualTo("device-C");
        assertThat(live.get(1).userAgent()).isEqualTo("device-A");
        // Hash form (sha256 = 64 hex chars) — never the raw token.
        assertThat(live).allSatisfy(s -> assertThat(s.sessionId()).hasSize(64));
    }

    @Test
    void listSessions_noSessions_returnsEmptyList() {
        assertThat(service.listSessions(999L)).isEmpty();
    }

    @Test
    void listSessions_sweepsDanglingSetMembersWhenRecordExpired() {
        User user = activeUser(50L);
        service.issue(user, "10.0.0.1", "ua");
        // Simulate the per-token record TTL'ing out while the SET entry lingers.
        String hash = sets.get("wedent:refresh:u:50").iterator().next();
        kv.remove("wedent:refresh:t:" + hash);

        List<RefreshTokenService.SessionView> live = service.listSessions(50L);

        assertThat(live).isEmpty();
        assertThat(sets.get("wedent:refresh:u:50")).doesNotContain(hash);
    }

    @Test
    void revokeSession_validIdForCaller_marksRevoked_andDropsFromSet() {
        User user = activeUser(50L);
        service.issue(user, "10.0.0.1", "ua");
        String hash = sets.get("wedent:refresh:u:50").iterator().next();

        boolean revoked = service.revokeSession(50L, hash);

        assertThat(revoked).isTrue();
        assertThat(sets.get("wedent:refresh:u:50")).doesNotContain(hash);
        assertThat(kv.get("wedent:refresh:t:" + hash)).contains("\"revokedAt\"");
    }

    @Test
    void revokeSession_idBelongsToOtherUser_returnsFalse_noStateChange() {
        User userA = activeUser(50L);
        service.issue(userA, "10.0.0.1", "ua");
        String hash = sets.get("wedent:refresh:u:50").iterator().next();
        String payloadBefore = kv.get("wedent:refresh:t:" + hash);

        // Caller pretends to be a different user trying to kill A's session.
        boolean revoked = service.revokeSession(99L, hash);

        assertThat(revoked).isFalse();
        assertThat(kv.get("wedent:refresh:t:" + hash)).isEqualTo(payloadBefore);
        assertThat(sets.get("wedent:refresh:u:50")).contains(hash);
    }

    @Test
    void revokeSession_unknownOrBlankId_returnsFalse() {
        assertThat(service.revokeSession(50L, null)).isFalse();
        assertThat(service.revokeSession(50L, "")).isFalse();
        assertThat(service.revokeSession(50L, "non-existent-hash")).isFalse();
    }

    @Test
    void revokeSession_alreadyRevoked_returnsFalse_doesNotDoubleRevoke() {
        User user = activeUser(50L);
        service.issue(user, "10.0.0.1", "ua");
        String hash = sets.get("wedent:refresh:u:50").iterator().next();

        assertThat(service.revokeSession(50L, hash)).isTrue();
        assertThat(service.revokeSession(50L, hash)).isFalse();
    }

    @Test
    void revokeAllForUser_marksAllActiveSessions_andDropsTheSet() {
        User user = activeUser(50L);
        service.issue(user, "10.0.0.1", "ua");
        service.issue(user, "10.0.0.2", "ua2");
        int sessions = sets.get("wedent:refresh:u:50").size();
        assertThat(sessions).isEqualTo(2);

        int revoked = service.revokeAllForUser(50L);

        assertThat(revoked).isEqualTo(2);
        assertThat(sets.get("wedent:refresh:u:50")).isNull();
        // Records themselves remain (revoked) so replay attempts still fire.
        assertThat(kv.values()).allMatch(v -> v.contains("\"revokedAt\""));
    }

    // ─────────────────────────────────────────────────────────────────────────

    private static User activeUser(long id) {
        Company company = Company.builder().name("Acme").build();
        company.setId(100L);
        User user = User.builder()
                .email("john@example.com")
                .firstName("John").lastName("Doe")
                .passwordHash("x")
                .status(UserStatus.ACTIVE)
                .company(company)
                .build();
        user.setId(id);
        return user;
    }
}
