package com.smartlearnly.backend.notification.dto;

import com.smartlearnly.backend.notification.entity.NotificationType;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        NotificationType type,
        String title,
        String body,
        String referenceType,
        UUID referenceId,
        String actionUrl,
        UUID actorId,
        String eventKey,
        Map<String, Object> payload,
        Instant readAt,
        Instant deliveredAt,
        Instant seenAt,
        Instant clickedAt,
        Instant createdAt
) {
}
