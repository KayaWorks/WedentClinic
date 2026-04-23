package com.wedent.clinic.auth;

import com.wedent.clinic.auth.entity.RefreshToken;
import com.wedent.clinic.auth.repository.RefreshTokenRepository;
import com.wedent.clinic.auth.service.RefreshTokenService;
import com.wedent.clinic.auth.service.impl.RefreshTokenServiceImpl;
import com.wedent.clinic.common.audit.AuditEventPublisher;
import com.wedent.clinic.common.exception.InvalidCredentialsException;
import com.wedent.clinic.company.entity.Company;
import com.wedent.clinic.security.JwtProperties;
import com.wedent.clinic.user.entity.User;
import com.wedent.clinic.user.entity.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefreshTokenServiceImplTest {

    private RefreshTokenRepository repository;
    private AuditEventPublisher auditEventPublisher;
    private RefreshTokenServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(RefreshTokenRepository.class);
        auditEventPublisher = Mockito.mock(AuditEventPublisher.class);
        JwtProperties props = new JwtProperties(
                new JwtProperties.Jwt(
                        "test-secret-test-secret-test-secret-test-secret-1234567890",
                        60, 14L, "wedent-clinic-test"),
                List.of());
        service = new RefreshTokenServiceImpl(repository, auditEventPublisher, props);

        // `save` must return the argument (with id assigned) — mimic Jpa identity behavior.
        when(repository.save(any(RefreshToken.class))).thenAnswer(inv -> {
            RefreshToken arg = inv.getArgument(0);
            if (arg.getId() == null) arg.setId(System.nanoTime());
            return arg;
        });
    }

    @Test
    void issue_persistsHashedToken_andReturnsRawSeparately() {
        User user = activeUser();

        RefreshTokenService.Issued issued = service.issue(user, "10.0.0.1", "JUnit/5");

        assertThat(issued.rawToken()).isNotBlank();
        assertThat(issued.row().getTokenHash())
                .isEqualTo(sha256Hex(issued.rawToken()))
                .hasSize(64);
        assertThat(issued.row().getUser()).isSameAs(user);
        assertThat(issued.row().getExpiresAt()).isAfter(Instant.now());
    }

    @Test
    void rotate_happyPath_revokesOld_issuesNew_andLinksChain() {
        User user = activeUser();
        String raw = "raw-original-token";
        RefreshToken existing = RefreshToken.builder()
                .user(user)
                .tokenHash(sha256Hex(raw))
                .issuedAt(Instant.now().minusSeconds(60))
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .build();
        existing.setId(42L);

        when(repository.findByTokenHash(sha256Hex(raw))).thenReturn(Optional.of(existing));

        RefreshTokenService.Rotated rotated = service.rotate(raw, "10.0.0.1", "JUnit/5");

        assertThat(rotated.newRawToken()).isNotBlank().isNotEqualTo(raw);
        assertThat(rotated.userId()).isEqualTo(user.getId());
        assertThat(existing.getRevokedAt()).isNotNull();
        assertThat(existing.getReplacedById()).isEqualTo(rotated.newRow().getId());
    }

    @Test
    void rotate_unknownToken_throwsInvalidCredentials() {
        when(repository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rotate("bogus", "10.0.0.1", null))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void rotate_onAlreadyRevokedToken_triggersReplayDefense() {
        User user = activeUser();
        String raw = "raw-stolen";
        RefreshToken already = RefreshToken.builder()
                .user(user)
                .tokenHash(sha256Hex(raw))
                .issuedAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .revokedAt(Instant.now().minusSeconds(30)) // already rotated
                .build();
        already.setId(7L);

        when(repository.findByTokenHash(sha256Hex(raw))).thenReturn(Optional.of(already));
        when(repository.revokeAllByUserId(eq(user.getId()), any())).thenReturn(3);

        assertThatThrownBy(() -> service.rotate(raw, "10.0.0.1", "JUnit/5"))
                .isInstanceOf(InvalidCredentialsException.class);

        // Defense-in-depth: all of user's live sessions must be killed on replay.
        verify(repository).revokeAllByUserId(eq(user.getId()), any());
    }

    @Test
    void rotate_onDisabledUser_throws() {
        User user = activeUser();
        user.setStatus(UserStatus.DISABLED);

        String raw = "raw-valid-but-user-locked";
        RefreshToken live = RefreshToken.builder()
                .user(user)
                .tokenHash(sha256Hex(raw))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .build();
        when(repository.findByTokenHash(sha256Hex(raw))).thenReturn(Optional.of(live));

        assertThatThrownBy(() -> service.rotate(raw, "10.0.0.1", null))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void revoke_knownToken_setsRevokedAt_once() {
        String raw = "raw-to-revoke";
        RefreshToken live = RefreshToken.builder()
                .tokenHash(sha256Hex(raw))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .build();
        when(repository.findByTokenHash(sha256Hex(raw))).thenReturn(Optional.of(live));

        service.revoke(raw);
        assertThat(live.getRevokedAt()).isNotNull();
        Instant first = live.getRevokedAt();

        // Calling revoke again must not overwrite the original revocation time —
        // audits depend on "revoked at" being the *first* revocation.
        service.revoke(raw);
        assertThat(live.getRevokedAt()).isEqualTo(first);
    }

    @Test
    void revoke_unknownOrBlankToken_isSilentNoop() {
        service.revoke(null);
        service.revoke("");
        service.revoke("   ");
        when(repository.findByTokenHash(any())).thenReturn(Optional.empty());
        service.revoke("nope");
        // No save call, no audit — just silent.
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static User activeUser() {
        Company company = Company.builder().name("Acme").build();
        company.setId(100L);
        User user = User.builder()
                .email("john@example.com")
                .firstName("John").lastName("Doe")
                .passwordHash("x")
                .status(UserStatus.ACTIVE)
                .company(company)
                .build();
        user.setId(50L);
        return user;
    }

    private static String sha256Hex(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
