package com.smartlearnly.backend.admin.settings.controller;

import com.smartlearnly.backend.admin.settings.dto.AssignmentAiSettingsResponse;
import com.smartlearnly.backend.admin.settings.dto.AssignmentAiSettingsUpdateRequest;
import com.smartlearnly.backend.admin.settings.service.SettingKeys;
import com.smartlearnly.backend.admin.settings.service.SystemSettingsService;
import com.smartlearnly.backend.admin.settings.service.SystemSettingsService.AssignmentAiSettings;
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
public class AiSettingsController {
    private final SystemSettingsService settingsService;
    private final AuditLogService auditLogService;
    private final AdminSettingsSupport support;

    // Tra cau hinh tao ban nhap bai tap va trang thai provider da san sang.
    @GetMapping("/ai/assignment-draft")
    public ApiResponse<AssignmentAiSettingsResponse> getAssignmentAiSettings() {
        AssignmentAiSettings settings = settingsService.resolveAssignmentAiSettings();
        AssignmentAiSettingsResponse response = new AssignmentAiSettingsResponse(
                settings.enabled(),
                settings.provider(),
                settings.isConfigured(),
                settings.model(),
                settings.fallbackModel(),
                settings.timeoutSeconds());
        return ApiResponse.success("Assignment AI settings loaded", response);
    }

    // Luu provider va model tao ban nhap bai tap roi ghi audit thay doi.
    @PutMapping("/ai/assignment-draft")
    @Transactional
    public ApiResponse<AssignmentAiSettingsResponse> updateAssignmentAiSettings(
            @Valid @RequestBody AssignmentAiSettingsUpdateRequest request) {
        UUID actor = support.currentUserId();
        settingsService.put(
                SettingKeys.ASSIGNMENT_AI_ENABLED,
                String.valueOf(Boolean.TRUE.equals(request.enabled())),
                false,
                actor);
        settingsService.put(SettingKeys.ASSIGNMENT_AI_PROVIDER, request.provider(), false, actor);
        support.putOptionalSecret(SettingKeys.ASSIGNMENT_AI_API_KEY, request.apiKey(), actor);
        settingsService.put(SettingKeys.ASSIGNMENT_AI_MODEL, request.model(), false, actor);
        settingsService.put(SettingKeys.ASSIGNMENT_AI_FALLBACK_MODEL, request.fallbackModel(), false, actor);
        settingsService.put(
                SettingKeys.ASSIGNMENT_AI_TIMEOUT_SECONDS,
                String.valueOf(request.timeoutSeconds()),
                false,
                actor);
        auditLogService.recordAction(
                support.actorLabel(),
                AuditAction.SETTINGS_UPDATE_ASSIGNMENT_AI,
                "system_settings",
                "assignment_ai");
        return getAssignmentAiSettings();
    }
}
