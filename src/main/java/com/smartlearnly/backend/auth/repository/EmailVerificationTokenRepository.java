package com.smartlearnly.backend.auth.repository;

import com.smartlearnly.backend.auth.entity.EmailVerificationToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, UUID> {
    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("""
            update EmailVerificationToken token
               set token.usedAt = :usedAt
             where token.user.id = :userId
               and token.usedAt is null
            """)
    int markAllUnusedAsUsed(@Param("userId") UUID userId, @Param("usedAt") Instant usedAt);
}
