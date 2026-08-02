package com.smartlearnly.backend.notification.service;

import com.smartlearnly.backend.common.api.PageResponse;
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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private static final int MAX_TITLE_LENGTH = 255;
    private static final int MAX_BODY_LENGTH = 4_000;
    private static final int MAX_REFERENCE_TYPE_LENGTH = 80;
    private static final int MAX_ACTION_URL_LENGTH = 500;
    private static final int MAX_EVENT_KEY_LENGTH = 200;

    private final NotificationRepository notificationRepository;
    private final CurrentUserService currentUserService;

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(String statusValue, String typeValue, int page, int size) {
        UserAccount actor = currentUserService.requireAuthenticatedUser();
        NotificationReadStatus status = NotificationReadStatus.from(statusValue);
        String type = toDatabaseType(parseType(typeValue));
        Page<Notification> result = notificationRepository.findForUser(
                actor.getId(),
                status != NotificationReadStatus.UNREAD,
                status != NotificationReadStatus.READ,
                type,
                PageRequest.of(page, size));
        return new PageResponse<>(
                result.getContent().stream().map(this::toResponse).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    private static NotificationType parseType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return NotificationType.valueOf(value.trim().replace('-', '_').toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Notification type is invalid");
        }
    }

    private static String toDatabaseType(NotificationType type) {
        return type == null ? null : type.name().toLowerCase(Locale.ROOT);
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse unreadCount() {
        UserAccount actor = currentUserService.requireAuthenticatedUser();
        return new UnreadCountResponse(notificationRepository.countByUserIdAndReadAtIsNullAndArchivedAtIsNull(actor.getId()));
    }

    @Transactional(readOnly = true)
    public NotificationResponse get(UUID notificationId) {
        UserAccount actor = currentUserService.requireAuthenticatedUser();
        return notificationRepository.findByIdAndUserIdAndArchivedAtIsNull(notificationId, actor.getId())
                .map(this::toResponse)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Notification was not found"));
    }

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
        return toResponse(notification);
    }

    @Transactional
    public UnreadCountResponse markAllRead() {
        UserAccount actor = currentUserService.requireAuthenticatedUser();
        notificationRepository.markAllReadForUser(actor.getId(), Instant.now());
        return unreadCount();
    }

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
        return toResponse(notificationRepository.save(notification));
    }

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
        return toResponse(notificationRepository.save(notification));
    }

    @Transactional
    public ArchivedCountResponse archiveAll() {
        UserAccount actor = currentUserService.requireAuthenticatedUser();
        return new ArchivedCountResponse(notificationRepository.archiveAllForUser(actor.getId(), Instant.now()));
    }

    @Transactional
    public Optional<NotificationResponse> emit(NotificationCreateCommand command) {
        return buildNotification(command)
                .flatMap(notification -> Optional.of(toResponse(notificationRepository.save(notification))));
    }

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
                .map(this::toResponse)
                .toList();
    }

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

    public NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getBody(),
                notification.getReferenceType(),
                notification.getReferenceId(),
                notification.getActionUrl(),
                notification.getActorId(),
                notification.getEventKey(),
                copyPayload(notification.getPayload()),
                notification.getReadAt(),
                notification.getDeliveredAt(),
                notification.getSeenAt(),
                notification.getClickedAt(),
                notification.getArchivedAt(),
                notification.getCreatedAt());
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
