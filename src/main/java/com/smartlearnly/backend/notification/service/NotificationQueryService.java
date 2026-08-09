package com.smartlearnly.backend.notification.service;

import com.smartlearnly.backend.common.api.PageResponse;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.notification.dto.NotificationResponse;
import com.smartlearnly.backend.notification.dto.UnreadCountResponse;
import com.smartlearnly.backend.notification.entity.Notification;
import com.smartlearnly.backend.notification.entity.NotificationType;
import com.smartlearnly.backend.notification.repository.NotificationRepository;
import com.smartlearnly.backend.user.entity.UserAccount;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Xử lý các thao tác đọc notification của người dùng.
 * Cung cấp danh sách notification gần nhất và đếm số chưa đọc.
 */
@Service
@RequiredArgsConstructor
public class NotificationQueryService {
    private final NotificationRepository notificationRepository;
    private final CurrentUserService currentUserService;

    /**
     * Lấy danh sách notification active của người dùng đang đăng nhập.
     *
     * @param page        số trang (0-indexed)
     * @param size        số notification mỗi trang
     * @return danh sách notification đã phân trang
     */
    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(int page, int size) {
        return list(page, size, "all", null);
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(int page, int size, String status) {
        return list(page, size, status, null);
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(int page, int size, String status, String type) {
        UserAccount actor = currentUserService.requireAuthenticatedUser();
        PageRequest pageRequest = PageRequest.of(page, size);
        Page<Notification> result = notificationRepository.findActiveForUserByStatusAndType(
                actor.getId(),
                normalizeStatus(status),
                normalizeType(type),
                pageRequest);
        return new PageResponse<>(
                result.getContent().stream().map(NotificationMapper::toResponse).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    /**
     * Đếm số notification chưa đọc của người dùng.
     *
     * @return số lượng notification chưa đọc
     */
    @Transactional(readOnly = true)
    public UnreadCountResponse unreadCount() {
        UserAccount actor = currentUserService.requireAuthenticatedUser();
        long count = notificationRepository.countByUserIdAndReadAtIsNullAndArchivedAtIsNull(actor.getId());
        return new UnreadCountResponse(count);
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return "all";
        }
        String normalized = status.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "unread", "read" -> normalized;
            default -> "all";
        };
    }

    private String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        String normalized = type.trim().replace('-', '_').toUpperCase(Locale.ROOT);
        try {
            return NotificationType.valueOf(normalized).name();
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

}
