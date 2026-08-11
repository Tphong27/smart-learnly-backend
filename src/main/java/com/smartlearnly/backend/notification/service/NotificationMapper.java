package com.smartlearnly.backend.notification.service;

import com.smartlearnly.backend.notification.dto.NotificationResponse;
import com.smartlearnly.backend.notification.entity.Notification;
import java.util.LinkedHashMap;

/**
 * Mapper chuyển đổi Notification entity sang NotificationResponse DTO.
 */
public final class NotificationMapper {
    private NotificationMapper() {
    }

    /**
     * Chuyển entity sang response DTO.
     *
     * @param notification entity notification
     * @return response DTO
     */
    public static NotificationResponse toResponse(Notification notification) {
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
                notification.getPayload() == null
                        ? new LinkedHashMap<>()
                        : new LinkedHashMap<>(notification.getPayload()),
                notification.getReadAt(),
                notification.getDeliveredAt(),
                notification.getSeenAt(),
                notification.getClickedAt(),
                notification.getArchivedAt(),
                notification.getCreatedAt());
    }

}
