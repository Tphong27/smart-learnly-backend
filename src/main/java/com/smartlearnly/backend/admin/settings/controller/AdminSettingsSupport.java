package com.smartlearnly.backend.admin.settings.controller;

import com.smartlearnly.backend.admin.settings.service.SystemSettingsService;
import com.smartlearnly.backend.common.security.AuthenticatedUserResolver;
import com.smartlearnly.backend.common.security.CurrentUser;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class AdminSettingsSupport {
    private final SystemSettingsService settingsService;
    private final AuthenticatedUserResolver authenticatedUserResolver;

    // Lưu, xóa hoặc giữ nguyên một cấu hình text tùy theo giá trị request.
    void putOptionalText(String key, String value, UUID actor) {
        if (value == null) {
            return;
        }
        if (value.isBlank()) {
            settingsService.delete(key);
            return;
        }
        settingsService.put(key, value, false, actor);
    }

    // Lưu secret mới, xóa khi rỗng và bỏ qua placeholder che secret hiện tại.
    void putOptionalSecret(String key, String value, UUID actor) {
        if (value == null || SystemSettingsService.SECRET_PLACEHOLDER.equals(value)) {
            return;
        }
        if (value.isBlank()) {
            settingsService.delete(key);
            return;
        }
        settingsService.put(key, value, true, actor);
    }

    // Lấy ID admin hiện tại để ghi người cập nhật cấu hình.
    UUID currentUserId() {
        return authenticatedUserResolver.resolve().map(CurrentUser::id).orElse(null);
    }

    // Lấy email admin hiện tại làm người nhận mặc định của email thử.
    String currentUserEmail() {
        return authenticatedUserResolver.resolve().map(CurrentUser::email).orElse(null);
    }

    // Tạo nhãn actor ổn định cho audit từ email hoặc ID người dùng.
    String actorLabel() {
        return authenticatedUserResolver.resolve()
                .map(user -> user.email() != null ? user.email() : String.valueOf(user.id()))
                .orElse("unknown");
    }
}
