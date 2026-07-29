package com.smartlearnly.backend.notification.dto;

import com.smartlearnly.backend.notification.entity.NotificationType;
import java.util.Map;
import java.util.UUID;

public record NotificationCreateCommand(
        UUID userId,
        NotificationType type,
        String title,
        String body,
        String referenceType,
        UUID referenceId,
        String actionUrl,
        UUID actorId,
        String eventKey,
        Map<String, Object> payload
) {
}
