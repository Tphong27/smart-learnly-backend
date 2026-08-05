package com.smartlearnly.backend.notification.service;

import com.smartlearnly.backend.common.api.PageResponse;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.notification.dto.NotificationResponse;
import com.smartlearnly.backend.notification.dto.UnreadCountResponse;
import com.smartlearnly.backend.notification.entity.Notification;
import com.smartlearnly.backend.notification.entity.NotificationType;
import com.smartlearnly.backend.notification.repository.NotificationRepository;
import com.smartlearnly.backend.user.entity.UserAccount;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Xử lý các thao tác đọc notification của người dùng.
 * Cung cấp danh sách, đếm số chưa đọc và chi tiết notification.
 */
@Service
@RequiredArgsConstructor
public class NotificationQueryService {
    private final NotificationRepository notificationRepository;
    private final CurrentUserService currentUserService;

    /**
     * Lấy danh sách notification của người dùng đang đăng nhập với bộ lọc.
     *
     * @param statusValue trạng thái đọc: all, unread, read
     * @param typeValue   loại notification: SYSTEM, ENROLLMENT, ASSIGNMENT, ...
     * @param page        số trang (0-indexed)
     * @param size        số notification mỗi trang
     * @return danh sách notification đã phân trang
     */
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

    /**
     * Lấy chi tiết một notification cụ thể của người dùng.
     *
     * @param notificationId ID của notification
     * @return thông tin notification
     * @throws BusinessException nếu không tìm thấy hoặc không thuộc về người dùng
     */
    @Transactional(readOnly = true)
    public NotificationResponse get(UUID notificationId) {
        UserAccount actor = currentUserService.requireAuthenticatedUser();
        return notificationRepository.findByIdAndUserIdAndArchivedAtIsNull(notificationId, actor.getId())
                .map(NotificationMapper::toResponse)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Notification was not found"));
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
}
