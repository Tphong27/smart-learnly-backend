package com.smartlearnly.backend.hls.repository;

import com.smartlearnly.backend.hls.entity.HlsActiveToken;
import com.smartlearnly.backend.hls.entity.HlsActiveTokenId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HlsActiveTokenRepository extends JpaRepository<HlsActiveToken, HlsActiveTokenId> {

    Optional<HlsActiveToken> findByUserIdAndLessonIdAndSessionId(
            UUID userId, UUID lessonId, String sessionId);

    boolean existsByUserIdAndLessonIdAndSessionId(
            UUID userId, UUID lessonId, String sessionId);

    boolean existsByUserIdAndLessonIdAndSessionIdAndTokenHash(
            UUID userId, UUID lessonId, String sessionId, String tokenHash);

    @Modifying
    @Query(value = """
            INSERT INTO public.hls_active_tokens 
            (user_id, lesson_id, session_id, token_hash, fingerprint, ip_hash, expires_at, created_at)
            VALUES (:userId, :lessonId, :sessionId, :tokenHash, :fingerprint, :ipHash, :expiresAt, NOW())
            ON CONFLICT (user_id, lesson_id, session_id) 
            DO UPDATE SET 
                token_hash = EXCLUDED.token_hash,
                fingerprint = EXCLUDED.fingerprint,
                ip_hash = EXCLUDED.ip_hash,
                expires_at = EXCLUDED.expires_at,
                created_at = NOW()
            """, nativeQuery = true)
    void upsertToken(
            @Param("userId") UUID userId,
            @Param("lessonId") UUID lessonId,
            @Param("sessionId") String sessionId,
            @Param("tokenHash") String tokenHash,
            @Param("fingerprint") String fingerprint,
            @Param("ipHash") String ipHash,
            @Param("expiresAt") Instant expiresAt);

    @Modifying
    @Query("DELETE FROM HlsActiveToken t WHERE t.userId = :userId AND t.lessonId = :lessonId AND t.sessionId = :sessionId")
    void deleteByUserIdAndLessonIdAndSessionId(
            @Param("userId") UUID userId,
            @Param("lessonId") UUID lessonId,
            @Param("sessionId") String sessionId);

    @Modifying
    @Query("DELETE FROM HlsActiveToken t WHERE t.expiresAt < :now")
    int deleteExpiredTokens(@Param("now") Instant now);
}
