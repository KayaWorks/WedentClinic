package com.wedent.clinic.auth.repository;

import com.wedent.clinic.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Bulk-revoke every still-live session for a user.  Used on logout-all
     * and on refresh-token replay detection.  Single UPDATE so we don't
     * round-trip per row.
     */
    @Modifying
    @Query("""
            UPDATE RefreshToken t
               SET t.revokedAt = :now
             WHERE t.user.id    = :userId
               AND t.revokedAt IS NULL
            """)
    int revokeAllByUserId(@Param("userId") Long userId, @Param("now") Instant now);
}
