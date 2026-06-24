package com.smartlearnly.backend.admin.settings.controller;

import com.smartlearnly.backend.admin.settings.dto.EmailSettingsResponse;
import com.smartlearnly.backend.admin.settings.dto.EmailSettingsUpdateRequest;
import com.smartlearnly.backend.admin.settings.dto.GoogleOAuthSettingsResponse;
import com.smartlearnly.backend.admin.settings.dto.GoogleOAuthSettingsUpdateRequest;
import com.smartlearnly.backend.admin.settings.dto.TestEmailRequest;
import com.smartlearnly.backend.admin.settings.service.SettingKeys;
import com.smartlearnly.backend.admin.settings.service.SystemSettingsService;
import com.smartlearnly.backend.auth.service.EmailService;
import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.common.audit.AuditLogService;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.AuthenticatedUserResolver;
import com.smartlearnly.backend.common.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
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
@Tag(name = "Admin System Settings", description = "Admin-only system configuration for email and OAuth providers.")
public class AdminSettingsController {
    private static final String GOOGLE_REDIRECT_URI_HINT = "/login/oauth2/code/google";

    private final SystemSettingsService settingsService;
    private final EmailService emailService;
    private final AuditLogService auditLogService;
    private final AuthenticatedUserResolver authenticatedUserResolver;

    @GetMapping("/email")
    @Operation(summary = "Get current email settings (secret masked)")
    public ApiResponse<EmailSettingsResponse> getEmailSettings() {
        EmailSettingsResponse response = new EmailSettingsResponse(
                settingsService.hasValue(SettingKeys.EMAIL_API_KEY),
                settingsService.getOrDefault(SettingKeys.EMAIL_FROM_NAME, null),
                settingsService.getOrDefault(SettingKeys.EMAIL_FROM_EMAIL, null),
                settingsService.getOrDefault(SettingKeys.EMAIL_REPLY_TO, null)
        );
        return ApiResponse.success("Email settings loaded", response);
    }

    @PutMapping("/email")
    @Operation(summary = "Update email settings")
    public ApiResponse<EmailSettingsResponse> updateEmailSettings(
            @Valid @RequestBody EmailSettingsUpdateRequest request
    ) {
        UUID actor = currentUserId();
        settingsService.put(SettingKeys.EMAIL_API_KEY, request.apiKey(), true, actor);
        settingsService.put(SettingKeys.EMAIL_FROM_NAME, request.fromName(), false, actor);
        settingsService.put(SettingKeys.EMAIL_FROM_EMAIL, request.fromEmail(), false, actor);
        // Reply-to is optional; allow clearing by sending a blank handled at service level.
        settingsService.put(SettingKeys.EMAIL_REPLY_TO, request.replyTo(), false, actor);
        auditLogService.record(actorLabel(), "SETTINGS_UPDATE_EMAIL", "system_settings", "email");
        return getEmailSettings();
    }

    @PostMapping("/email/test")
    @Operation(summary = "Send a test email using the active configuration")
    public ApiResponse<Void> testEmail(@Valid @RequestBody(required = false) TestEmailRequest request) {
        String recipient = (request != null && request.to() != null && !request.to().isBlank())
                ? request.to()
                : currentUserEmail();
        if (recipient == null || recipient.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "No recipient available for the test email");
        }
        try {
            emailService.sendTestEmail(recipient);
        } catch (IllegalStateException exception) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE, exception.getMessage());
        } catch (RuntimeException exception) {
            throw new BusinessException(
                    ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                    "Failed to send test email. Check the email configuration."
            );
        }
        return ApiResponse.success("Test email sent to " + recipient);
    }

    @GetMapping("/oauth/google")
    @Operation(summary = "Get current Google OAuth settings (secret masked)")
    public ApiResponse<GoogleOAuthSettingsResponse> getGoogleOAuth() {
        GoogleOAuthSettingsResponse response = new GoogleOAuthSettingsResponse(
                settingsService.getOrDefault(SettingKeys.GOOGLE_CLIENT_ID, null),
                settingsService.hasValue(SettingKeys.GOOGLE_CLIENT_SECRET),
                settingsService.getOrDefault(SettingKeys.GOOGLE_SCOPE, "openid,profile,email"),
                GOOGLE_REDIRECT_URI_HINT
        );
        return ApiResponse.success("Google OAuth settings loaded", response);
    }

    @PutMapping("/oauth/google")
    @Operation(summary = "Update Google OAuth settings")
    public ApiResponse<GoogleOAuthSettingsResponse> updateGoogleOAuth(
            @Valid @RequestBody GoogleOAuthSettingsUpdateRequest request
    ) {
        UUID actor = currentUserId();
        settingsService.put(SettingKeys.GOOGLE_CLIENT_ID, request.clientId(), false, actor);
        settingsService.put(SettingKeys.GOOGLE_CLIENT_SECRET, request.clientSecret(), true, actor);
        settingsService.put(SettingKeys.GOOGLE_SCOPE, request.scope(), false, actor);
        auditLogService.record(actorLabel(), "SETTINGS_UPDATE_OAUTH_GOOGLE", "system_settings", "oauth.google");
        return getGoogleOAuth();
    }

    private UUID currentUserId() {
        return authenticatedUserResolver.resolve().map(CurrentUser::id).orElse(null);
    }

    private String currentUserEmail() {
        return authenticatedUserResolver.resolve().map(CurrentUser::email).orElse(null);
    }

    private String actorLabel() {
        return authenticatedUserResolver.resolve()
                .map(user -> user.email() != null ? user.email() : String.valueOf(user.id()))
                .orElse("unknown");
    }
}
