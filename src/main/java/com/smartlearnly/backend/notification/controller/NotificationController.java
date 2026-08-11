package com.smartlearnly.backend.notification.controller;

import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.common.api.PageResponse;
import com.smartlearnly.backend.notification.dto.NotificationResponse;
import com.smartlearnly.backend.notification.dto.UnreadCountResponse;
import com.smartlearnly.backend.notification.service.NotificationService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller xử lý các API liên quan đến notification của người dùng.
 * Cung cấp endpoints để xem, đánh dấu đã đọc và lưu trữ notification.
 */
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/v1/notifications")
@PreAuthorize("isAuthenticated()")
public class NotificationController {
    private final NotificationService notificationService;

    /**
     * Lấy danh sách notification của người dùng đang đăng nhập.
     */
    @GetMapping
    public ApiResponse<PageResponse<NotificationResponse>> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "all") String status,
            @RequestParam(required = false) String type) {
        return ApiResponse.success(
                "Notifications loaded successfully",
                notificationService.list(page, size, status, type));
    }

    /**
     * Lấy số notification chưa đọc của người dùng.
     */
    @GetMapping("/unread-count")
    public ApiResponse<UnreadCountResponse> unreadCount() {
        return ApiResponse.success(
                "Unread notification count loaded successfully",
                notificationService.unreadCount());
    }

    /**
     * Đánh dấu một notification là đã đọc.
     */
    @PatchMapping("/{notificationId}/read")
    public ApiResponse<NotificationResponse> markRead(@PathVariable UUID notificationId) {
        return ApiResponse.success(
                "Notification marked as read",
                notificationService.markRead(notificationId));
    }

    /**
     * Ghi nhận thao tác click vào notification.
     */
    @PatchMapping("/{notificationId}/clicked")
    public ApiResponse<NotificationResponse> recordClick(@PathVariable UUID notificationId) {
        return ApiResponse.success(
                "Notification click recorded",
                notificationService.recordClick(notificationId));
    }

    /**
     * Lưu trữ một notification.
     */
    @PatchMapping("/{notificationId}/archive")
    public ApiResponse<NotificationResponse> archive(@PathVariable UUID notificationId) {
        return ApiResponse.success(
                "Notification archived",
                notificationService.archive(notificationId));
    }

    @PatchMapping("/archive-all")
    public ApiResponse<UnreadCountResponse> archiveAll() {
        return ApiResponse.success(
                "Notifications archived",
                notificationService.archiveAll());
    }

    /**
     * Đánh dấu tất cả notification là đã đọc.
     */
    @PatchMapping("/read-all")
    public ApiResponse<UnreadCountResponse> markAllRead() {
        return ApiResponse.success(
                "Notifications marked as read",
                notificationService.markAllRead());
    }

}
