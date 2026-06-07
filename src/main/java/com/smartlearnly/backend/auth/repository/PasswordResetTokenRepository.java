package com.smartlearnly.backend.auth.repository;

import com.smartlearnly.backend.auth.entity.PasswordResetToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("""
            update PasswordResetToken token
               set token.usedAt = :usedAt
             where token.user.id = :userId
               and token.usedAt is null
            """)
    int markAllUnusedAsUsed(@Param("userId") UUID userId, @Param("usedAt") Instant usedAt);
}
