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
    /** Lấy notification của một người dùng theo thứ tự mới nhất. */
    Page<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    /** Tìm notification theo ID và chủ sở hữu. */
    Optional<Notification> findByIdAndUserId(UUID id, UUID userId);

    /** Đếm notification chưa đọc của một người dùng. */
    long countByUserIdAndReadAtIsNull(UUID userId);

    /** Kiểm tra event key đã được phát cho người dùng hay chưa. */
    boolean existsByUserIdAndEventKey(UUID userId, String eventKey);

    /** Đánh dấu toàn bộ notification chưa đọc của người dùng là đã đọc. */
    @Modifying
    @Query("""
            UPDATE Notification notification
            SET notification.readAt = :now,
                notification.seenAt = COALESCE(notification.seenAt, :now),
                notification.updatedAt = :now
            WHERE notification.userId = :userId
              AND notification.readAt IS NULL
            """)
    int markAllReadForUser(@Param("userId") UUID userId, @Param("now") Instant now);

    /** Xóa notification đã đọc và cũ hơn thời điểm retention. */
    @Modifying
    @Query("""
            DELETE FROM Notification notification
            WHERE notification.readAt IS NOT NULL
              AND notification.createdAt < :cutoff
            """)
    int deleteReadCreatedBefore(@Param("cutoff") Instant cutoff);
}
