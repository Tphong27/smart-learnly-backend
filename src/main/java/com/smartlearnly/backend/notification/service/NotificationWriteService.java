package com.smartlearnly.backend.notification.service;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.notification.dto.ArchivedCountResponse;
import com.smartlearnly.backend.notification.dto.NotificationCreateCommand;
import com.smartlearnly.backend.notification.dto.NotificationResponse;
import com.smartlearnly.backend.notification.dto.UnreadCountResponse;
import com.smartlearnly.backend.notification.entity.Notification;
import com.smartlearnly.backend.notification.entity.NotificationType;
import com.smartlearnly.backend.notification.repository.NotificationRepository;
import com.smartlearnly.backend.user.entity.UserAccount;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Xử lý các thao tác ghi notification: đánh dấu đã đọc, lưu trữ,
 * tạo và xóa notification.
 */
@Service
@RequiredArgsConstructor
public class NotificationWriteService {
    private static final int MAX_TITLE_LENGTH = 255;
    private static final int MAX_BODY_LENGTH = 4_000;
    private static final int MAX_REFERENCE_TYPE_LENGTH = 80;
    private static final int MAX_ACTION_URL_LENGTH = 500;
    private static final int MAX_EVENT_KEY_LENGTH = 200;

    private final NotificationRepository notificationRepository;
    private final CurrentUserService currentUserService;
    private final NotificationQueryService queryService;

    /**
     * Đánh dấu một notification là đã đọc và đã xem.
     *
     * @param notificationId ID của notification
     * @return notification sau khi cập nhật
     */
    @Transactional
    public NotificationResponse markRead(UUID notificationId) {
        UserAccount actor = currentUserService.requireAuthenticatedUser();
        Notification notification = findActiveOwnedNotification(notificationId, actor.getId());
        Instant now = Instant.now();
        if (notification.getReadAt() == null) {
            notification.setReadAt(now);
        }
        if (notification.getSeenAt() == null) {
            notification.setSeenAt(now);
        }
        notification = notificationRepository.save(notification);
        return NotificationMapper.toResponse(notification);
    }

    /**
     * Đánh dấu tất cả notification của người dùng là đã đọc.
     *
     * @return số notification đã đọc
     */
    @Transactional
    public UnreadCountResponse markAllRead() {
        UserAccount actor = currentUserService.requireAuthenticatedUser();
        notificationRepository.markAllReadForUser(actor.getId(), Instant.now());
        return queryService.unreadCount();
    }

    /**
     * Ghi nhận thao tác click vào notification: đánh dấu đã đọc, đã xem và đã click.
     *
     * @param notificationId ID của notification
     * @return notification sau khi cập nhật
     */
    @Transactional
    public NotificationResponse recordClick(UUID notificationId) {
        UserAccount actor = currentUserService.requireAuthenticatedUser();
        Notification notification = findActiveOwnedNotification(notificationId, actor.getId());
        Instant now = Instant.now();
        if (notification.getReadAt() == null) {
            notification.setReadAt(now);
        }
        if (notification.getSeenAt() == null) {
            notification.setSeenAt(now);
        }
        if (notification.getClickedAt() == null) {
            notification.setClickedAt(now);
        }
        return NotificationMapper.toResponse(notificationRepository.save(notification));
    }

    /**
     * Lưu trữ một notification: đánh dấu đã đọc, đã xem và đặt thời điểm lưu trữ.
     *
     * @param notificationId ID của notification
     * @return notification sau khi cập nhật
     */
    @Transactional
    public NotificationResponse archive(UUID notificationId) {
        UserAccount actor = currentUserService.requireAuthenticatedUser();
        Notification notification = findActiveOwnedNotification(notificationId, actor.getId());
        Instant now = Instant.now();
        if (notification.getReadAt() == null) {
            notification.setReadAt(now);
        }
        if (notification.getSeenAt() == null) {
            notification.setSeenAt(now);
        }
        notification.setArchivedAt(now);
        return NotificationMapper.toResponse(notificationRepository.save(notification));
    }

    /**
     * Lưu trữ tất cả notification của người dùng.
     *
     * @return số notification đã lưu trữ
     */
    @Transactional
    public ArchivedCountResponse archiveAll() {
        UserAccount actor = currentUserService.requireAuthenticatedUser();
        return new ArchivedCountResponse(notificationRepository.archiveAllForUser(actor.getId(), Instant.now()));
    }

    /**
     * Tạo một notification mới cho người dùng.
     * Bỏ qua nếu notification với cùng eventKey đã tồn tại.
     *
     * @param command thông tin tạo notification
     * @return notification đã tạo hoặc empty nếu bị trùng eventKey
     */
    @Transactional
    public Optional<NotificationResponse> emit(NotificationCreateCommand command) {
        return buildNotification(command)
                .map(notification -> NotificationMapper.toResponse(notificationRepository.save(notification)));
    }

    /**
     * Tạo nhiều notification cùng lúc cho một nhóm người dùng.
     * Loại bỏ trùng lặp eventKey trong cùng batch.
     *
     * @param commands danh sách thông tin tạo notification
     * @return danh sách notification đã tạo
     */
    @Transactional
    public List<NotificationResponse> emitAll(Collection<NotificationCreateCommand> commands) {
        if (commands == null || commands.isEmpty()) {
            return List.of();
        }
        Set<String> batchEventKeys = new HashSet<>();
        List<Notification> notifications = new ArrayList<>();
        for (NotificationCreateCommand command : commands) {
            Optional<Notification> candidate = buildNotification(command);
            if (candidate.isEmpty()) {
                continue;
            }
            Notification notification = candidate.get();
            String eventKey = notification.getEventKey();
            String batchKey = eventKey == null ? null : notification.getUserId() + ":" + eventKey;
            if (batchKey != null && !batchEventKeys.add(batchKey)) {
                continue;
            }
            notifications.add(notification);
        }
        return notificationRepository.saveAll(notifications)
                .stream()
                .map(NotificationMapper::toResponse)
                .toList();
    }

    /**
     * Xóa các notification đã đọc hoặc đã lưu trữ trước thời điểm cutoff.
     * Dùng cho cleanup theo retention policy.
     *
     * @param cutoff thời điểm cutoff, notification trước thời điểm này sẽ bị xóa
     * @return số notification đã xóa
     */
    @Transactional
    public int cleanupReadOrArchivedCreatedBefore(Instant cutoff) {
        if (cutoff == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Notification retention cutoff is required");
        }
        return notificationRepository.deleteReadOrArchivedCreatedBefore(cutoff);
    }

    private Notification findActiveOwnedNotification(UUID notificationId, UUID userId) {
        return notificationRepository.findByIdAndUserIdAndArchivedAtIsNull(notificationId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Notification was not found"));
    }

    private Optional<Notification> buildNotification(NotificationCreateCommand command) {
        UUID userId = require(command.userId(), "Notification user is required");
        String eventKey = normalize(command.eventKey(), MAX_EVENT_KEY_LENGTH);
        if (eventKey != null && notificationRepository.existsByUserIdAndEventKey(userId, eventKey)) {
            return Optional.empty();
        }

        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType(require(command.type(), "Notification type is required"));
        notification.setTitle(requireText(command.title(), "Notification title is required", MAX_TITLE_LENGTH));
        notification.setBody(normalize(command.body(), MAX_BODY_LENGTH));
        notification.setReferenceType(normalize(command.referenceType(), MAX_REFERENCE_TYPE_LENGTH));
        notification.setReferenceId(command.referenceId());
        notification.setActionUrl(normalize(command.actionUrl(), MAX_ACTION_URL_LENGTH));
        notification.setActorId(command.actorId());
        notification.setEventKey(eventKey);
        notification.setPayload(copyPayload(command.payload()));
        return Optional.of(notification);
    }

    private static Map<String, Object> copyPayload(Map<String, Object> payload) {
        return payload == null ? new LinkedHashMap<>() : new LinkedHashMap<>(payload);
    }

    private static String requireText(String value, String message, int maxLength) {
        String normalized = normalize(value, maxLength);
        if (normalized == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, message);
        }
        return normalized;
    }

    private static String normalize(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > maxLength) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Notification text exceeds " + maxLength + " characters");
        }
        return trimmed;
    }

    private static <T> T require(T value, String message) {
        if (value == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, message);
        }
        return value;
    }
}
