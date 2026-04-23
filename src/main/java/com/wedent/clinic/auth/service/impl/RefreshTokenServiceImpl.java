package com.wedent.clinic.auth.service.impl;

import com.wedent.clinic.auth.entity.RefreshToken;
import com.wedent.clinic.auth.repository.RefreshTokenRepository;
import com.wedent.clinic.auth.service.RefreshTokenService;
import com.wedent.clinic.common.audit.AuditEventPublisher;
import com.wedent.clinic.common.audit.event.AuditEvent;
import com.wedent.clinic.common.audit.event.AuditEventType;
import com.wedent.clinic.common.exception.InvalidCredentialsException;
import com.wedent.clinic.security.JwtProperties;
import com.wedent.clinic.user.entity.User;
import com.wedent.clinic.user.entity.UserStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;

/**
 * Refresh-token lifecycle:
 *
 * <pre>
 *   login          →  issue()    →  (new row, raw token to client)
 *   refresh        →  rotate()   →  revoke old, issue new (chained via replaced_by_id)
 *   replay detect  →  rotate() on an already-revoked row → revoke all user sessions
 *   logout         →  revoke()   →  single-row revocation
 * </pre>
 *
 * The raw token value is generated from 32 bytes of {@link SecureRandom} and base64url
 * encoded.  Only its SHA-256 hex digest is persisted — a DB dump cannot be replayed.
 */
@Slf4j
@Service
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private static final int RAW_TOKEN_BYTES = 32; // 256 bits of entropy
    private static final SecureRandom RNG = new SecureRandom();

    private final RefreshTokenRepository repository;
    private final AuditEventPublisher auditEventPublisher;
    private final long expirationMillis;

    public RefreshTokenServiceImpl(RefreshTokenRepository repository,
                                   AuditEventPublisher auditEventPublisher,
                                   JwtProperties jwtProperties) {
        this.repository = repository;
        this.auditEventPublisher = auditEventPublisher;
        this.expirationMillis = jwtProperties.jwt().refreshTokenExpirationDaysOrDefault() * 24L * 60L * 60L * 1000L;
    }

    @Override
    @Transactional
    public Issued issue(User user, String ipAddress, String userAgent) {
        String rawToken = generateRawToken();
        RefreshToken row = persistNew(user, rawToken, ipAddress, userAgent);
        return new Issued(row, rawToken);
    }

    @Override
    @Transactional
    public Rotated rotate(String rawRefreshToken, String ipAddress, String userAgent) {
        String hash = sha256Hex(rawRefreshToken);
        RefreshToken current = repository.findByTokenHash(hash)
                .orElseThrow(InvalidCredentialsException::new);

        Instant now = Instant.now();

        // --- Replay detection ---------------------------------------------------------
        // If a token that has already been rotated shows up, the only way that can happen
        // is if an attacker captured it before the legitimate user rotated it (or someone
        // is testing with a stale token).  Either way the chain is compromised; kill all
        // sessions for this user and force a fresh login.
        if (current.getRevokedAt() != null) {
            Long userId = current.getUser().getId();
            int revoked = repository.revokeAllByUserId(userId, now);
            log.warn("Refresh-token replay detected for userId={} — revoked {} live sessions", userId, revoked);
            auditEventPublisher.publish(AuditEvent.builder(AuditEventType.TOKEN_REFRESH_REPLAY)
                    .actorUserId(userId)
                    .ipAddress(ipAddress)
                    .detail(Map.of("revokedCount", revoked))
                    .build());
            throw new InvalidCredentialsException();
        }

        if (!current.isActive(now)) {
            throw new InvalidCredentialsException();
        }

        User user = current.getUser();
        if (user.getStatus() != UserStatus.ACTIVE) {
            // Disabled/locked user should not be able to trade an old session for a new one.
            throw new InvalidCredentialsException();
        }

        String newRaw = generateRawToken();
        RefreshToken replacement = persistNew(user, newRaw, ipAddress, userAgent);

        current.setRevokedAt(now);
        current.setReplacedById(replacement.getId());

        return new Rotated(replacement, newRaw, user.getId());
    }

    @Override
    @Transactional
    public void revoke(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        String hash = sha256Hex(rawRefreshToken);
        repository.findByTokenHash(hash).ifPresent(row -> {
            if (row.getRevokedAt() == null) {
                row.setRevokedAt(Instant.now());
            }
        });
    }

    @Override
    public long expirationMillis() {
        return expirationMillis;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Internals
    // ─────────────────────────────────────────────────────────────────────────────

    private RefreshToken persistNew(User user, String rawToken, String ipAddress, String userAgent) {
        Instant now = Instant.now();
        RefreshToken row = RefreshToken.builder()
                .user(user)
                .tokenHash(sha256Hex(rawToken))
                .issuedAt(now)
                .expiresAt(now.plus(expirationMillis, ChronoUnit.MILLIS))
                .ipAddress(ipAddress)
                .userAgent(truncate(userAgent, 255))
                .build();
        return repository.save(row);
    }

    private static String generateRawToken() {
        byte[] bytes = new byte[RAW_TOKEN_BYTES];
        RNG.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandatory in every JVM.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
