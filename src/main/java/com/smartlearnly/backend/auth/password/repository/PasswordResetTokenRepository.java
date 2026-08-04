package com.smartlearnly.backend.auth.password.repository;

import com.smartlearnly.backend.auth.password.entity.PasswordResetToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {
    // Tìm reset token bằng bản hash, không truy vấn token rõ từ database.
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    @Modifying
    // Vô hiệu hóa mọi reset token cũ chưa dùng của người dùng.
    @Query("""
            update PasswordResetToken token
               set token.usedAt = :usedAt
             where token.user.id = :userId
               and token.usedAt is null
            """)
    int markAllUnusedAsUsed(@Param("userId") UUID userId, @Param("usedAt") Instant usedAt);
}
