package com.smartlearnly.backend.auth.session.repository;

import com.smartlearnly.backend.auth.session.entity.RefreshToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    // Tìm session bằng bản hash của refresh token được client gửi lên.
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    // Thu hồi toàn bộ session còn hoạt động của người dùng tại một thời điểm.
    @Query("""
            update RefreshToken token
               set token.revokedAt = :revokedAt
             where token.user.id = :userId
               and token.revokedAt is null
            """)
    int revokeAllActiveByUserId(@Param("userId") UUID userId, @Param("revokedAt") Instant revokedAt);
}
