package com.wedent.clinic.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
public class JwtTokenProvider {

    private static final String CLAIM_USER_ID = "uid";
    private static final String CLAIM_COMPANY_ID = "cid";
    private static final String CLAIM_CLINIC_ID = "clid";
    private static final String CLAIM_ROLES = "roles";

    private final SecretKey secretKey;
    private final long expirationMillis;
    private final String issuer;

    public JwtTokenProvider(JwtProperties properties) {
        byte[] keyBytes = properties.jwt().secret().getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT secret must be at least 32 bytes (256 bits).");
        }
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMillis = properties.jwt().accessTokenExpirationMinutes() * 60_000L;
        this.issuer = properties.jwt().issuer();
    }

    public String generateAccessToken(AuthenticatedUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .subject(user.email())
                // jti = the unique token identifier; required for server-side revocation
                // (see AccessTokenBlacklist).  Without it we could only blacklist per-subject
                // which would log out every parallel session the user has.
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMillis)))
                .claim(CLAIM_USER_ID, user.userId())
                .claim(CLAIM_COMPANY_ID, user.companyId())
                .claim(CLAIM_CLINIC_ID, user.clinicId())
                .claim(CLAIM_ROLES, user.roles())
                .signWith(secretKey)
                .compact();
    }

    public Optional<Claims> parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(claims);
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Invalid JWT token: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    public AuthenticatedUser toAuthenticatedUser(Claims claims) {
        Long userId = ((Number) claims.get(CLAIM_USER_ID)).longValue();
        Long companyId = claims.get(CLAIM_COMPANY_ID) == null ? null : ((Number) claims.get(CLAIM_COMPANY_ID)).longValue();
        Long clinicId = claims.get(CLAIM_CLINIC_ID) == null ? null : ((Number) claims.get(CLAIM_CLINIC_ID)).longValue();
        List<String> roleList = (List<String>) claims.getOrDefault(CLAIM_ROLES, List.of());
        return new AuthenticatedUser(
                userId,
                claims.getSubject(),
                companyId,
                clinicId,
                java.util.Set.copyOf(roleList),
                roleList.stream()
                        .map(r -> "ROLE_" + r)
                        .map(org.springframework.security.core.authority.SimpleGrantedAuthority::new)
                        .map(a -> (org.springframework.security.core.GrantedAuthority) a)
                        .toList()
        );
    }

    public long getExpirationMillis() {
        return expirationMillis;
    }
}
