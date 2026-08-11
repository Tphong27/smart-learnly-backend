package com.smartlearnly.backend.admin.settings.controller;

import com.smartlearnly.backend.admin.settings.dto.GoogleMeetSettingsResponse;
import com.smartlearnly.backend.admin.settings.dto.GoogleMeetSettingsUpdateRequest;
import com.smartlearnly.backend.admin.settings.dto.GoogleOAuthSettingsResponse;
import com.smartlearnly.backend.admin.settings.dto.GoogleOAuthSettingsUpdateRequest;
import com.smartlearnly.backend.admin.settings.service.SettingKeys;
import com.smartlearnly.backend.admin.settings.service.SystemSettingsService;
import com.smartlearnly.backend.admin.settings.service.SystemSettingsService.GoogleMeetSettings;
import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.common.audit.AuditAction;
import com.smartlearnly.backend.common.audit.AuditLogService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/settings")
@PreAuthorize("hasRole('ADMIN')")
public class GoogleSettingsController {
    private static final String GOOGLE_REDIRECT_URI_HINT = "/login/oauth2/code/google";

    private final SystemSettingsService settingsService;
    private final AuditLogService auditLogService;
    private final AdminSettingsSupport support;

    // Trả trạng thái cấu hình OAuth Google mà không lộ client secret.
    @GetMapping("/oauth/google")
    public ApiResponse<GoogleOAuthSettingsResponse> getGoogleOAuth() {
        GoogleOAuthSettingsResponse response = new GoogleOAuthSettingsResponse(
                settingsService.hasValue(SettingKeys.GOOGLE_CLIENT_ID),
                settingsService.hasValue(SettingKeys.GOOGLE_CLIENT_SECRET),
                settingsService.getOrDefault(SettingKeys.GOOGLE_SCOPE, "openid,profile,email"),
                GOOGLE_REDIRECT_URI_HINT);
        return ApiResponse.success("Google OAuth settings loaded", response);
    }

    // Lưu client OAuth Google và ghi audit người thay đổi.
    @PutMapping("/oauth/google")
    @Transactional
    public ApiResponse<GoogleOAuthSettingsResponse> updateGoogleOAuth(
            @Valid @RequestBody GoogleOAuthSettingsUpdateRequest request) {
        UUID actor = support.currentUserId();
        settingsService.put(SettingKeys.GOOGLE_CLIENT_ID, request.clientId(), true, actor);
        settingsService.put(SettingKeys.GOOGLE_CLIENT_SECRET, request.clientSecret(), true, actor);
        settingsService.put(SettingKeys.GOOGLE_SCOPE, request.scope(), false, actor);
        auditLogService.recordAction(
                support.actorLabel(),
                AuditAction.SETTINGS_UPDATE_OAUTH_GOOGLE,
                "system_settings",
                "oauth.google");
        return getGoogleOAuth();
    }

    // Trả trạng thái bật và trạng thái secret của tích hợp Google Meet.
    @GetMapping("/integrations/google-meet")
    public ApiResponse<GoogleMeetSettingsResponse> getGoogleMeetSettings() {
        GoogleMeetSettings settings = settingsService.resolveGoogleMeetSettings();
        GoogleMeetSettingsResponse response = new GoogleMeetSettingsResponse(
                settings.enabled(),
                settingsService.hasValue(SettingKeys.GOOGLE_MEET_REFRESH_TOKEN)
                        || settings.refreshToken() != null && !settings.refreshToken().isBlank());
        return ApiResponse.success("Google Meet settings loaded", response);
    }

    // Bật/tắt Google Meet, cập nhật refresh token khi có và ghi audit.
    @PutMapping("/integrations/google-meet")
    @Transactional
    public ApiResponse<GoogleMeetSettingsResponse> updateGoogleMeetSettings(
            @Valid @RequestBody GoogleMeetSettingsUpdateRequest request) {
        UUID actor = support.currentUserId();
        settingsService.put(
                SettingKeys.GOOGLE_MEET_ENABLED,
                String.valueOf(Boolean.TRUE.equals(request.enabled())),
                false,
                actor);
        support.putOptionalSecret(SettingKeys.GOOGLE_MEET_REFRESH_TOKEN, request.refreshToken(), actor);
        auditLogService.recordAction(
                support.actorLabel(),
                AuditAction.SETTINGS_UPDATE_GOOGLE_MEET,
                "system_settings",
                "google_meet");
        return getGoogleMeetSettings();
    }
}
