package com.smartlearnly.backend.admin.settings.controller;

import com.smartlearnly.backend.admin.settings.dto.EmailSettingsResponse;
import com.smartlearnly.backend.admin.settings.dto.EmailSettingsUpdateRequest;
import com.smartlearnly.backend.admin.settings.dto.TestEmailRequest;
import com.smartlearnly.backend.admin.settings.service.SettingKeys;
import com.smartlearnly.backend.admin.settings.service.SystemSettingsService;
import com.smartlearnly.backend.auth.service.EmailService;
import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.common.audit.AuditAction;
import com.smartlearnly.backend.common.audit.AuditLogService;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/settings")
@PreAuthorize("hasRole('ADMIN')")
public class EmailSettingsController {
    private final SystemSettingsService settingsService;
    private final EmailService emailService;
    private final AuditLogService auditLogService;
    private final AdminSettingsSupport support;

    // Trả cấu hình email hiện tại và chỉ cho biết API key có tồn tại hay không.
    @GetMapping("/email")
    public ApiResponse<EmailSettingsResponse> getEmailSettings() {
        EmailSettingsResponse response = new EmailSettingsResponse(
                settingsService.hasValue(SettingKeys.EMAIL_API_KEY),
                settingsService.getOrDefault(SettingKeys.EMAIL_FROM_NAME, null),
                settingsService.getOrDefault(SettingKeys.EMAIL_FROM_EMAIL, null),
                settingsService.getOrDefault(SettingKeys.EMAIL_REPLY_TO, null));
        return ApiResponse.success("Email settings loaded", response);
    }

    // Lưu cấu hình gửi email, bảo vệ API key và ghi audit người thay đổi.
    @PutMapping("/email")
    @Transactional
    public ApiResponse<EmailSettingsResponse> updateEmailSettings(
            @Valid @RequestBody EmailSettingsUpdateRequest request) {
        UUID actor = support.currentUserId();
        settingsService.put(SettingKeys.EMAIL_API_KEY, request.apiKey(), true, actor);
        settingsService.put(SettingKeys.EMAIL_FROM_NAME, request.fromName(), false, actor);
        settingsService.put(SettingKeys.EMAIL_FROM_EMAIL, request.fromEmail(), false, actor);
        support.putOptionalText(SettingKeys.EMAIL_REPLY_TO, request.replyTo(), actor);
        auditLogService.recordAction(
                support.actorLabel(), AuditAction.SETTINGS_UPDATE_EMAIL, "system_settings", "email");
        return getEmailSettings();
    }

    // Gửi email thử tới địa chỉ request hoặc email của admin hiện tại.
    @PostMapping("/email/test")
    public ApiResponse<Void> testEmail(@Valid @RequestBody(required = false) TestEmailRequest request) {
        String recipient = request != null && request.to() != null && !request.to().isBlank()
                ? request.to()
                : support.currentUserEmail();
        if (recipient == null || recipient.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "No recipient available for the test email");
        }
        try {
            emailService.sendTestEmail(recipient);
        }
        catch (BusinessException exception) {
            throw exception;
        }
        catch (IllegalStateException exception) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE, exception.getMessage());
        }
        catch (RuntimeException exception) {
            throw new BusinessException(
                    ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                    "Failed to send test email. Check the email configuration.");
        }
        return ApiResponse.success("Test email sent to " + recipient);
    }
}
