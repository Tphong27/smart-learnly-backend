package com.smartlearnly.backend.notification.service;

import com.smartlearnly.backend.common.api.PageResponse;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.notification.dto.NotificationCreateCommand;
import com.smartlearnly.backend.notification.dto.NotificationResponse;
import com.smartlearnly.backend.notification.dto.UnreadCountResponse;
import com.smartlearnly.backend.notification.entity.Notification;
import com.smartlearnly.backend.notification.entity.NotificationType;
import com.smartlearnly.backend.notification.repository.NotificationRepository;
import com.smartlearnly.backend.user.entity.UserAccount;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
        NotificationType type = parseType(typeValue);
        Page<Notification> result = notificationRepository.findForUser(
                actor.getId(),
                status != NotificationReadStatus.UNREAD,
                status != NotificationReadStatus.READ,
                type,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
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

    @Transactional(readOnly = true)
    public UnreadCountResponse unreadCount() {
        UserAccount actor = currentUserService.requireAuthenticatedUser();
        return new UnreadCountResponse(notificationRepository.countByUserIdAndReadAtIsNull(actor.getId()));
    }

    @Transactional(readOnly = true)
    public NotificationResponse get(UUID notificationId) {
        UserAccount actor = currentUserService.requireAuthenticatedUser();
        return notificationRepository.findByIdAndUserId(notificationId, actor.getId())
                .map(this::toResponse)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Notification was not found"));
    }

    @Transactional
    public NotificationResponse markRead(UUID notificationId) {
        UserAccount actor = currentUserService.requireAuthenticatedUser();
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, actor.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Notification was not found"));
        if (notification.getReadAt() == null) {
            notification.setReadAt(Instant.now());
            notification = notificationRepository.save(notification);
        }
        return toResponse(notification);
    }

    @Transactional
    public UnreadCountResponse markAllRead() {
        UserAccount actor = currentUserService.requireAuthenticatedUser();
        notificationRepository.markAllReadForUser(actor.getId(), Instant.now());
        return unreadCount();
    }

    @Transactional
    public Optional<NotificationResponse> emit(NotificationCreateCommand command) {
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
        return Optional.of(toResponse(notificationRepository.save(notification)));
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
