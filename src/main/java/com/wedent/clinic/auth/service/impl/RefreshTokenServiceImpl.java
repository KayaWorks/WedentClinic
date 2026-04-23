package com.wedent.clinic.auth.service.impl;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wedent.clinic.auth.service.RefreshTokenService;
import com.wedent.clinic.common.audit.AuditEventPublisher;
import com.wedent.clinic.common.audit.event.AuditEvent;
import com.wedent.clinic.common.audit.event.AuditEventType;
import com.wedent.clinic.common.exception.InvalidCredentialsException;
import com.wedent.clinic.config.CacheProperties;
import com.wedent.clinic.security.JwtProperties;
import com.wedent.clinic.user.entity.User;
import com.wedent.clinic.user.entity.UserStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis-only refresh-token store.
 *
 * <h3>Key layout</h3>
 * <pre>
 *   {prefix}refresh:t:{sha256(token)}   JSON  (per-token record)
 *   {prefix}refresh:u:{userId}          SET   (hashes belonging to the user)
 * </pre>
 *
 * <h3>Invariants</h3>
 * <ul>
 *   <li>The raw token is never persisted — only its SHA-256 hex digest. A
 *       Redis dump cannot be replayed against the app.</li>
 *   <li>A per-token record's TTL equals the token's natural expiry.  Revoked
 *       records keep that TTL so we can still catch a replay attempt inside
 *       the original window.</li>
 *   <li>The per-user {@code SET} is best-effort — entries may outlive their
 *       token's TTL briefly.  We always re-check the per-token key before
 *       trusting membership.</li>
 * </ul>
 */
@Slf4j
@Service
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private static final int RAW_TOKEN_BYTES = 32; // 256 bits
    private static final SecureRandom RNG = new SecureRandom();
    private static final String KEY_TOKEN = "refresh:t:";
    private static final String KEY_USER  = "refresh:u:";

    private final StringRedisTemplate redis;
    private final AuditEventPublisher auditEventPublisher;
    private final ObjectMapper mapper;
    private final long expirationMillis;
    private final String keyPrefix;

    public RefreshTokenServiceImpl(StringRedisTemplate redis,
                                   AuditEventPublisher auditEventPublisher,
                                   JwtProperties jwtProperties,
                                   CacheProperties cacheProperties) {
        this.redis = redis;
        this.auditEventPublisher = auditEventPublisher;
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.expirationMillis = jwtProperties.jwt().refreshTokenExpirationDaysOrDefault()
                * 24L * 60L * 60L * 1000L;
        this.keyPrefix = cacheProperties.keyPrefix();
    }

    @Override
    public Issued issue(User user, String ipAddress, String userAgent) {
        String rawToken = generateRawToken();
        persistNew(user.getId(), rawToken, ipAddress, userAgent);
        return new Issued(rawToken);
    }

    @Override
    public Rotated rotate(String rawRefreshToken, String ipAddress, String userAgent) {
        String hash = sha256Hex(rawRefreshToken);
        Record existing = loadOrThrow(hash);

        // --- Replay detection ----------------------------------------------------------
        // A refresh token that has already been rotated coming back in means the chain
        // is compromised; torch every session for the owning user and refuse the trade.
        if (existing.revokedAt != null) {
            int revoked = revokeAllForUser(existing.userId);
            log.warn("Refresh-token replay detected userId={} — revoked {} live sessions",
                    existing.userId, revoked);
            auditEventPublisher.publish(AuditEvent.builder(AuditEventType.TOKEN_REFRESH_REPLAY)
                    .actorUserId(existing.userId)
                    .ipAddress(ipAddress)
                    .detail(Map.of("revokedCount", revoked))
                    .build());
            throw new InvalidCredentialsException();
        }

        Instant now = Instant.now();
        if (existing.expiresAt.isBefore(now)) {
            throw new InvalidCredentialsException();
        }

        String newRaw = generateRawToken();
        String newHash = sha256Hex(newRaw);

        // Old record: mark rotated + link to successor; preserve TTL for replay window.
        Record rotated = existing.withRevocation(now, newHash);
        Long remainingMillis = redis.getExpire(tokenKey(hash), TimeUnit.MILLISECONDS);
        if (remainingMillis == null || remainingMillis <= 0) {
            remainingMillis = 60_000L; // minimal sliver so replay attempts don't look valid
        }
        redis.opsForValue().set(tokenKey(hash), toJson(rotated), Duration.ofMillis(remainingMillis));

        // New record keyed by new hash
        persistNew(existing.userId, newRaw, ipAddress, userAgent);

        return new Rotated(newRaw, existing.userId);
    }

    @Override
    public void revoke(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) return;
        String hash = sha256Hex(rawRefreshToken);
        String key = tokenKey(hash);
        String payload = redis.opsForValue().get(key);
        if (payload == null) return;
        Record record = fromJson(payload);
        if (record.revokedAt != null) return; // preserve first revocation timestamp

        Long remainingMillis = redis.getExpire(key, TimeUnit.MILLISECONDS);
        if (remainingMillis == null || remainingMillis <= 0) {
            redis.delete(key);
            return;
        }
        redis.opsForValue().set(key, toJson(record.withRevocation(Instant.now(), null)),
                Duration.ofMillis(remainingMillis));
        redis.opsForSet().remove(userKey(record.userId), hash);
    }

    @Override
    public int revokeAllForUser(Long userId) {
        Set<String> hashes = redis.opsForSet().members(userKey(userId));
        if (hashes == null || hashes.isEmpty()) return 0;
        int revoked = 0;
        Instant now = Instant.now();
        for (String hash : hashes) {
            String key = tokenKey(hash);
            String payload = redis.opsForValue().get(key);
            if (payload == null) continue;
            Record record = fromJson(payload);
            if (record.revokedAt != null) continue;
            Long remainingMillis = redis.getExpire(key, TimeUnit.MILLISECONDS);
            if (remainingMillis == null || remainingMillis <= 0) continue;
            redis.opsForValue().set(key, toJson(record.withRevocation(now, null)),
                    Duration.ofMillis(remainingMillis));
            revoked++;
        }
        redis.delete(userKey(userId));
        return revoked;
    }

    @Override
    public long expirationMillis() {
        return expirationMillis;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Internals
    // ─────────────────────────────────────────────────────────────────────────────

    /** Legacy validation: disabled users may not trade a token.  Called from AuthServiceImpl. */
    public static void assertUserCanRefresh(User user) {
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new InvalidCredentialsException();
        }
    }

    private void persistNew(Long userId, String rawToken, String ipAddress, String userAgent) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusMillis(expirationMillis);
        String hash = sha256Hex(rawToken);
        Record record = new Record(
                userId, now, expiresAt, null, null,
                ipAddress, truncate(userAgent, 255)
        );
        redis.opsForValue().set(tokenKey(hash), toJson(record), Duration.ofMillis(expirationMillis));
        redis.opsForSet().add(userKey(userId), hash);
        // Keep the set a little longer than the longest possible token so GC is eventual.
        redis.expire(userKey(userId), Duration.ofMillis(expirationMillis).plusDays(1));
    }

    private Record loadOrThrow(String hash) {
        String payload = redis.opsForValue().get(tokenKey(hash));
        if (payload == null) throw new InvalidCredentialsException();
        return fromJson(payload);
    }

    private String tokenKey(String hash) { return keyPrefix + KEY_TOKEN + hash; }
    private String userKey(Long userId)  { return keyPrefix + KEY_USER + userId; }

    private static String generateRawToken() {
        byte[] bytes = new byte[RAW_TOKEN_BYTES];
        RNG.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256Hex(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    private String toJson(Record record) {
        try { return mapper.writeValueAsString(record); }
        catch (JsonProcessingException e) { throw new IllegalStateException("refresh token serialization failed", e); }
    }

    private Record fromJson(String json) {
        try { return mapper.readValue(json, Record.class); }
        catch (JsonProcessingException e) { throw new IllegalStateException("refresh token deserialization failed", e); }
    }

    /**
     * On-the-wire shape for a token record. Public so Jackson can bind to it;
     * otherwise this is a pure internal structure.
     */
    public record Record(
            Long userId,
            Instant issuedAt,
            Instant expiresAt,
            Instant revokedAt,
            String replacedByHash,
            String ipAddress,
            String userAgent
    ) {
        Record withRevocation(Instant at, String replacedBy) {
            return new Record(userId, issuedAt, expiresAt, at,
                    replacedBy != null ? replacedBy : replacedByHash, ipAddress, userAgent);
        }
    }
}
