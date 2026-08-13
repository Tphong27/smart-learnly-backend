package com.smartlearnly.backend.notification.service;

import com.smartlearnly.backend.common.api.PageResponse;
import com.smartlearnly.backend.notification.dto.NotificationCreateCommand;
import com.smartlearnly.backend.notification.dto.NotificationResponse;
import com.smartlearnly.backend.notification.dto.UnreadCountResponse;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Facade service để maintain backward compatibility với các consumers hiện tại.
 * Chuyển tiếp calls đến QueryService và WriteService tương ứng.
 * 
 * @deprecated Sử dụng {@link NotificationQueryService} hoặc {@link NotificationWriteService} trực tiếp.
 */
@Deprecated
@Service
@RequiredArgsConstructor
public class NotificationService {
    private final NotificationQueryService queryService;
    private final NotificationWriteService writeService;

    /**
     * Lấy danh sách notification của người dùng.
     */
    public PageResponse<NotificationResponse> list(int page, int size) {
        return queryService.list(page, size);
    }

    /**
     * Đếm số notification chưa đọc.
     */
    public UnreadCountResponse unreadCount() {
        return queryService.unreadCount();
    }

    /**
     * Đánh dấu tất cả notification là đã đọc.
     */
    public UnreadCountResponse markAllRead() {
        return writeService.markAllRead();
    }

    /**
     * Ghi nhận thao tác click vào notification.
     */
    public NotificationResponse recordClick(UUID notificationId) {
        return writeService.recordClick(notificationId);
    }

    /**
     * Tạo một notification mới.
     */
    public Optional<NotificationResponse> emit(NotificationCreateCommand command) {
        return writeService.emit(command);
    }

    /**
     * Tạo nhiều notification cùng lúc.
     */
    public List<NotificationResponse> emitAll(Collection<NotificationCreateCommand> commands) {
        return writeService.emitAll(commands);
    }

    /**
     * Xóa notification cũ theo retention policy.
     */
    public int cleanupReadCreatedBefore(java.time.Instant cutoff) {
        return writeService.cleanupReadCreatedBefore(cutoff);
    }
}
