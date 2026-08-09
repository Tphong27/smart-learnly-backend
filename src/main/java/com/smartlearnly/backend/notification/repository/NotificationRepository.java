package com.smartlearnly.backend.notification.repository;

import com.smartlearnly.backend.notification.entity.Notification;
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
    @Query(value = """
            SELECT notification.*
            FROM public.notifications notification
            WHERE notification.user_id = :userId
              AND notification.archived_at IS NULL
            ORDER BY notification.created_at DESC
            """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM public.notifications notification
                    WHERE notification.user_id = :userId
                      AND notification.archived_at IS NULL
                    """,
            nativeQuery = true)
    Page<Notification> findActiveForUser(
            @Param("userId") UUID userId,
            Pageable pageable);

    Page<Notification> findByUserIdAndArchivedAtIsNullAndReadAtIsNullOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<Notification> findByUserIdAndArchivedAtIsNullAndReadAtIsNotNullOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    @Query(value = """
            SELECT notification.*
            FROM public.notifications notification
            WHERE notification.user_id = :userId
              AND notification.archived_at IS NULL
              AND (
                    CAST(:status AS text) = 'all'
                    OR (CAST(:status AS text) = 'unread' AND notification.read_at IS NULL)
                    OR (CAST(:status AS text) = 'read' AND notification.read_at IS NOT NULL)
              )
              AND (
                    CAST(:type AS text) IS NULL
                    OR notification.type::text = CAST(:type AS text)
              )
            ORDER BY notification.created_at DESC
            """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM public.notifications notification
                    WHERE notification.user_id = :userId
                      AND notification.archived_at IS NULL
                      AND (
                            CAST(:status AS text) = 'all'
                            OR (CAST(:status AS text) = 'unread' AND notification.read_at IS NULL)
                            OR (CAST(:status AS text) = 'read' AND notification.read_at IS NOT NULL)
                      )
                      AND (
                            CAST(:type AS text) IS NULL
                            OR notification.type::text = CAST(:type AS text)
                      )
                    """,
            nativeQuery = true)
    Page<Notification> findActiveForUserByStatusAndType(
            @Param("userId") UUID userId,
            @Param("status") String status,
            @Param("type") String type,
            Pageable pageable);

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
            SET notification.readAt = COALESCE(notification.readAt, :now),
                notification.seenAt = COALESCE(notification.seenAt, :now),
                notification.archivedAt = :now,
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
