package com.smartlearnly.backend.admin.settings.controller;

import com.smartlearnly.backend.admin.settings.dto.AssignmentAiSettingsResponse;
import com.smartlearnly.backend.admin.settings.dto.AssignmentAiSettingsUpdateRequest;
import com.smartlearnly.backend.admin.settings.dto.QuestionImageImportSettingsResponse;
import com.smartlearnly.backend.admin.settings.dto.QuestionImageImportSettingsUpdateRequest;
import com.smartlearnly.backend.admin.settings.service.SettingKeys;
import com.smartlearnly.backend.admin.settings.service.SystemSettingsService;
import com.smartlearnly.backend.admin.settings.service.SystemSettingsService.AssignmentAiSettings;
import com.smartlearnly.backend.admin.settings.service.SystemSettingsService.QuestionImageImportSettings;
import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.common.audit.AuditAction;
import com.smartlearnly.backend.common.audit.AuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Admin System Settings", description = "Admin-only AI provider configuration.")
public class AiSettingsController {
    private final SystemSettingsService settingsService;
    private final AuditLogService auditLogService;
    private final AdminSettingsSupport support;

    // Trả cấu hình AI nhập câu hỏi từ ảnh và trạng thái provider đã sẵn sàng.
    @GetMapping("/ai/question-image-import")
    @Operation(summary = "Get current question image import settings (secret masked)")
    public ApiResponse<QuestionImageImportSettingsResponse> getQuestionImageImportSettings() {
        QuestionImageImportSettings settings = settingsService.resolveQuestionImageImportSettings();
        QuestionImageImportSettingsResponse response = new QuestionImageImportSettingsResponse(
                settings.enabled(),
                settings.provider(),
                settings.isConfigured(),
                settings.model(),
                settings.timeoutSeconds(),
                settings.maxFileSizeMb(),
                settings.maxFiles());
        return ApiResponse.success("Question image import settings loaded", response);
    }

    // Lưu provider, model và giới hạn nhập câu hỏi từ ảnh rồi ghi audit.
    @PutMapping("/ai/question-image-import")
    @Transactional
    @Operation(summary = "Update question image import settings")
    public ApiResponse<QuestionImageImportSettingsResponse> updateQuestionImageImportSettings(
            @Valid @RequestBody QuestionImageImportSettingsUpdateRequest request) {
        UUID actor = support.currentUserId();
        settingsService.put(
                SettingKeys.QUESTION_IMAGE_IMPORT_ENABLED,
                String.valueOf(Boolean.TRUE.equals(request.enabled())),
                false,
                actor);
        settingsService.put(SettingKeys.QUESTION_IMAGE_IMPORT_PROVIDER, request.provider(), false, actor);
        support.putOptionalSecret(SettingKeys.QUESTION_IMAGE_IMPORT_API_KEY, request.apiKey(), actor);
        settingsService.put(SettingKeys.QUESTION_IMAGE_IMPORT_MODEL, request.model(), false, actor);
        settingsService.put(
                SettingKeys.QUESTION_IMAGE_IMPORT_TIMEOUT_SECONDS,
                String.valueOf(request.timeoutSeconds()),
                false,
                actor);
        settingsService.put(
                SettingKeys.QUESTION_IMAGE_IMPORT_MAX_FILE_SIZE_MB,
                String.valueOf(request.maxFileSizeMb()),
                false,
                actor);
        settingsService.put(
                SettingKeys.QUESTION_IMAGE_IMPORT_MAX_FILES,
                String.valueOf(request.maxFiles()),
                false,
                actor);
        auditLogService.recordAction(
                support.actorLabel(),
                AuditAction.SETTINGS_UPDATE_QUESTION_IMAGE_IMPORT,
                "system_settings",
                "question_image_import");
        return getQuestionImageImportSettings();
    }

    // Trả cấu hình tạo bản nháp bài tập và trạng thái provider đã sẵn sàng.
    @GetMapping("/ai/assignment-draft")
    @Operation(summary = "Get current assignment AI draft settings (secret masked)")
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

    // Lưu provider và model tạo bản nháp bài tập rồi ghi audit thay đổi.
    @PutMapping("/ai/assignment-draft")
    @Transactional
    @Operation(summary = "Update assignment AI draft settings")
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
