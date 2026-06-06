package com.smartlearnly.backend.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnTransformer;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "users", schema = "public")
public class UserAccount {
    @Id
    private UUID id;

    @Column(name = "auth_user_id")
    private UUID authUserId;

    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "google_id")
    private String googleId;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(nullable = false, columnDefinition = "user_role")
    @ColumnTransformer(write = "?::user_role")
    private String role;

    @Column(nullable = false, columnDefinition = "user_status")
    @ColumnTransformer(write = "?::user_status")
    private String status;

    @Column
    private String bio;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "password_changed_at")
    private Instant passwordChangedAt;

    @Column(name = "ai_message_count", nullable = false)
    private Integer aiMessageCount;

    @Column(name = "ai_quota_reset_date")
    private LocalDate aiQuotaResetDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (aiMessageCount == null) {
            aiMessageCount = 0;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public boolean isEmailVerified() {
        return emailVerifiedAt != null || !"pending_verify".equalsIgnoreCase(status);
    }
}
