package com.smartlearnly.backend.hls.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@IdClass(HlsActiveTokenId.class)
@Table(name = "hls_active_tokens", schema = "public")
public class HlsActiveToken {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Id
    @Column(name = "lesson_id")
    private UUID lessonId;

    @Id
    @Column(name = "session_id")
    private String sessionId;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(name = "fingerprint")
    private String fingerprint;

    @Column(name = "ip_hash")
    private String ipHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
    }

    @PreUpdate
    void preUpdate() {
        // No update timestamp needed - tokens are immutable once created
    }

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }
}
