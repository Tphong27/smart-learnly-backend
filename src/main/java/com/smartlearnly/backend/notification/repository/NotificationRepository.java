package com.smartlearnly.backend.notification.repository;

import com.smartlearnly.backend.notification.entity.Notification;
import com.smartlearnly.backend.notification.entity.NotificationType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    @Query("""
            SELECT notification
            FROM Notification notification
            WHERE notification.userId = :userId
              AND notification.archivedAt IS NULL
              AND (:type IS NULL OR notification.type = :type)
              AND (:includeRead = true OR notification.readAt IS NULL)
              AND (:includeUnread = true OR notification.readAt IS NOT NULL)
            ORDER BY notification.createdAt DESC
            """)
    Page<Notification> findForUser(
            @Param("userId") UUID userId,
            @Param("includeRead") boolean includeRead,
            @Param("includeUnread") boolean includeUnread,
            @Param("type") NotificationType type,
            Pageable pageable);

    Optional<Notification> findByIdAndUserId(UUID id, UUID userId);

    Optional<Notification> findByIdAndUserIdAndArchivedAtIsNull(UUID id, UUID userId);

    long countByUserIdAndReadAtIsNullAndArchivedAtIsNull(UUID userId);

    boolean existsByUserIdAndEventKey(UUID userId, String eventKey);

    @Modifying
    @Query("""
            UPDATE Notification notification
            SET notification.readAt = :now,
                notification.seenAt = COALESCE(notification.seenAt, :now),
                notification.updatedAt = :now
            WHERE notification.userId = :userId
              AND notification.archivedAt IS NULL
              AND notification.readAt IS NULL
            """)
    int markAllReadForUser(@Param("userId") UUID userId, @Param("now") Instant now);

    @Modifying
    @Query("""
            UPDATE Notification notification
            SET notification.archivedAt = :now,
                notification.readAt = COALESCE(notification.readAt, :now),
                notification.seenAt = COALESCE(notification.seenAt, :now),
                notification.updatedAt = :now
            WHERE notification.userId = :userId
              AND notification.archivedAt IS NULL
            """)
    int archiveAllForUser(@Param("userId") UUID userId, @Param("now") Instant now);

    @Modifying
    @Query("""
            DELETE FROM Notification notification
            WHERE (notification.archivedAt IS NOT NULL OR notification.readAt IS NOT NULL)
              AND notification.createdAt < :cutoff
            """)
    int deleteReadOrArchivedCreatedBefore(@Param("cutoff") Instant cutoff);
}
