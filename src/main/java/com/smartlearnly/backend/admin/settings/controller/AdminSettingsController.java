package com.smartlearnly.backend.admin.settings.controller;

import com.smartlearnly.backend.admin.settings.dto.AssignmentAiSettingsResponse;
import com.smartlearnly.backend.admin.settings.dto.AssignmentAiSettingsUpdateRequest;
import com.smartlearnly.backend.admin.settings.dto.EmailSettingsResponse;
import com.smartlearnly.backend.admin.settings.dto.EmailSettingsUpdateRequest;
import com.smartlearnly.backend.admin.settings.dto.GoogleMeetSettingsResponse;
import com.smartlearnly.backend.admin.settings.dto.GoogleMeetSettingsUpdateRequest;
import com.smartlearnly.backend.admin.settings.dto.GoogleOAuthSettingsResponse;
import com.smartlearnly.backend.admin.settings.dto.GoogleOAuthSettingsUpdateRequest;
import com.smartlearnly.backend.admin.settings.dto.QuestionImageImportSettingsResponse;
import com.smartlearnly.backend.admin.settings.dto.QuestionImageImportSettingsUpdateRequest;
import com.smartlearnly.backend.admin.settings.dto.SePayBankDisplaySettingsResponse;
import com.smartlearnly.backend.admin.settings.dto.SePayBankDisplaySettingsUpdateRequest;
import com.smartlearnly.backend.admin.settings.dto.SePayReconciliationRunResponse;
import com.smartlearnly.backend.admin.settings.dto.SePayRuntimeSettingsResponse;
import com.smartlearnly.backend.admin.settings.dto.SePayRuntimeSettingsUpdateRequest;
import com.smartlearnly.backend.admin.settings.dto.TestEmailRequest;
import com.smartlearnly.backend.admin.settings.service.SettingKeys;
import com.smartlearnly.backend.admin.settings.service.SystemSettingsService;
import com.smartlearnly.backend.admin.settings.service.SystemSettingsService.AssignmentAiSettings;
import com.smartlearnly.backend.admin.settings.service.SystemSettingsService.GoogleMeetSettings;
import com.smartlearnly.backend.admin.settings.service.SystemSettingsService.QuestionImageImportSettings;
import com.smartlearnly.backend.admin.settings.service.SystemSettingsService.SePayBankDisplaySettings;
import com.smartlearnly.backend.admin.settings.service.SystemSettingsService.SePayRuntimeSettings;
import com.smartlearnly.backend.auth.service.EmailService;
import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.payment.sepay.SePayReconciliationService;
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
@Tag(name = "Admin System Settings", description = "Admin-only system configuration for email, OAuth, integrations, and AI providers.")
public class AdminSettingsController {
    private static final String GOOGLE_REDIRECT_URI_HINT = "/login/oauth2/code/google";

    private final SystemSettingsService settingsService;
    private final EmailService emailService;
    private final AuditLogService auditLogService;
    private final AuthenticatedUserResolver authenticatedUserResolver;
    private final SePayReconciliationService sePayReconciliationService;

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
    @Transactional
    @Operation(summary = "Update email settings")
    public ApiResponse<EmailSettingsResponse> updateEmailSettings(
            @Valid @RequestBody EmailSettingsUpdateRequest request
    ) {
        UUID actor = currentUserId();
        settingsService.put(SettingKeys.EMAIL_API_KEY, request.apiKey(), true, actor);
        settingsService.put(SettingKeys.EMAIL_FROM_NAME, request.fromName(), false, actor);
        settingsService.put(SettingKeys.EMAIL_FROM_EMAIL, request.fromEmail(), false, actor);
        putOptionalText(SettingKeys.EMAIL_REPLY_TO, request.replyTo(), actor);
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
        } catch (BusinessException exception) {
            throw exception;
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
    @Operation(summary = "Get current Google OAuth settings (secrets masked)")
    public ApiResponse<GoogleOAuthSettingsResponse> getGoogleOAuth() {
        GoogleOAuthSettingsResponse response = new GoogleOAuthSettingsResponse(
                settingsService.hasValue(SettingKeys.GOOGLE_CLIENT_ID),
                settingsService.hasValue(SettingKeys.GOOGLE_CLIENT_SECRET),
                settingsService.getOrDefault(SettingKeys.GOOGLE_SCOPE, "openid,profile,email"),
                GOOGLE_REDIRECT_URI_HINT
        );
        return ApiResponse.success("Google OAuth settings loaded", response);
    }

    @PutMapping("/oauth/google")
    @Transactional
    @Operation(summary = "Update Google OAuth settings")
    public ApiResponse<GoogleOAuthSettingsResponse> updateGoogleOAuth(
            @Valid @RequestBody GoogleOAuthSettingsUpdateRequest request
    ) {
        UUID actor = currentUserId();
        settingsService.put(SettingKeys.GOOGLE_CLIENT_ID, request.clientId(), true, actor);
        settingsService.put(SettingKeys.GOOGLE_CLIENT_SECRET, request.clientSecret(), true, actor);
        settingsService.put(SettingKeys.GOOGLE_SCOPE, request.scope(), false, actor);
        auditLogService.record(actorLabel(), "SETTINGS_UPDATE_OAUTH_GOOGLE", "system_settings", "oauth.google");
        return getGoogleOAuth();
    }

    @GetMapping("/integrations/google-meet")
    @Operation(summary = "Get current Google Meet integration settings (secret masked)")
    public ApiResponse<GoogleMeetSettingsResponse> getGoogleMeetSettings() {
        GoogleMeetSettings settings = settingsService.resolveGoogleMeetSettings();
        GoogleMeetSettingsResponse response = new GoogleMeetSettingsResponse(
                settings.enabled(),
                settingsService.hasValue(SettingKeys.GOOGLE_MEET_REFRESH_TOKEN)
                        || (settings.refreshToken() != null && !settings.refreshToken().isBlank())
        );
        return ApiResponse.success("Google Meet settings loaded", response);
    }

    @PutMapping("/integrations/google-meet")
    @Transactional
    @Operation(summary = "Update Google Meet integration settings")
    public ApiResponse<GoogleMeetSettingsResponse> updateGoogleMeetSettings(
            @Valid @RequestBody GoogleMeetSettingsUpdateRequest request
    ) {
        UUID actor = currentUserId();
        settingsService.put(SettingKeys.GOOGLE_MEET_ENABLED, String.valueOf(Boolean.TRUE.equals(request.enabled())), false, actor);
        putOptionalSecret(SettingKeys.GOOGLE_MEET_REFRESH_TOKEN, request.refreshToken(), actor);
        auditLogService.record(actorLabel(), "SETTINGS_UPDATE_GOOGLE_MEET", "system_settings", "google_meet");
        return getGoogleMeetSettings();
    }

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
                settings.maxFiles()
        );
        return ApiResponse.success("Question image import settings loaded", response);
    }

    @PutMapping("/ai/question-image-import")
    @Transactional
    @Operation(summary = "Update question image import settings")
    public ApiResponse<QuestionImageImportSettingsResponse> updateQuestionImageImportSettings(
            @Valid @RequestBody QuestionImageImportSettingsUpdateRequest request
    ) {
        UUID actor = currentUserId();
        settingsService.put(SettingKeys.QUESTION_IMAGE_IMPORT_ENABLED, String.valueOf(Boolean.TRUE.equals(request.enabled())), false, actor);
        settingsService.put(SettingKeys.QUESTION_IMAGE_IMPORT_PROVIDER, request.provider(), false, actor);
        putOptionalSecret(SettingKeys.QUESTION_IMAGE_IMPORT_API_KEY, request.apiKey(), actor);
        settingsService.put(SettingKeys.QUESTION_IMAGE_IMPORT_MODEL, request.model(), false, actor);
        settingsService.put(SettingKeys.QUESTION_IMAGE_IMPORT_TIMEOUT_SECONDS, String.valueOf(request.timeoutSeconds()), false, actor);
        settingsService.put(SettingKeys.QUESTION_IMAGE_IMPORT_MAX_FILE_SIZE_MB, String.valueOf(request.maxFileSizeMb()), false, actor);
        settingsService.put(SettingKeys.QUESTION_IMAGE_IMPORT_MAX_FILES, String.valueOf(request.maxFiles()), false, actor);
        auditLogService.record(actorLabel(), "SETTINGS_UPDATE_QUESTION_IMAGE_IMPORT", "system_settings", "question_image_import");
        return getQuestionImageImportSettings();
    }

    @GetMapping("/integrations/sepay/bank-display")
    @Operation(summary = "Get current SePay bank display settings")
    public ApiResponse<SePayBankDisplaySettingsResponse> getSePayBankDisplaySettings() {
        return ApiResponse.success("SePay bank display settings loaded", toSePayBankDisplayResponse());
    }

    @PutMapping("/integrations/sepay/bank-display")
    @Transactional
    @Operation(summary = "Update SePay bank display settings")
    public ApiResponse<SePayBankDisplaySettingsResponse> updateSePayBankDisplaySettings(
            @Valid @RequestBody SePayBankDisplaySettingsUpdateRequest request
    ) {
        UUID actor = currentUserId();
        settingsService.put(SettingKeys.SEPAY_ACCOUNT_NUMBER, request.accountNumber().trim(), false, actor);
        settingsService.put(SettingKeys.SEPAY_BANK_NAME, request.bankName().trim(), false, actor);
        settingsService.put(SettingKeys.SEPAY_ACCOUNT_NAME, request.accountName().trim(), false, actor);
        auditLogService.record(actorLabel(), "SETTINGS_UPDATE_SEPAY_BANK_DISPLAY", "system_settings", "payment.sepay.bank_display");
        return getSePayBankDisplaySettings();
    }

    @GetMapping("/integrations/sepay/runtime")
    @Operation(summary = "Get current SePay runtime secret settings (secret masked)")
    public ApiResponse<SePayRuntimeSettingsResponse> getSePayRuntimeSettings() {
        return ApiResponse.success("SePay runtime settings loaded", toSePayRuntimeResponse());
    }

    @PutMapping("/integrations/sepay/runtime")
    @Transactional
    @Operation(summary = "Update SePay runtime secret settings")
    public ApiResponse<SePayRuntimeSettingsResponse> updateSePayRuntimeSettings(
            @Valid @RequestBody SePayRuntimeSettingsUpdateRequest request
    ) {
        UUID actor = currentUserId();
        putOptionalSecret(SettingKeys.SEPAY_API_TOKEN, request.apiToken(), actor);
        putOptionalSecret(SettingKeys.SEPAY_WEBHOOK_SECRET, request.webhookSecret(), actor);
        auditLogService.record(actorLabel(), "SETTINGS_UPDATE_SEPAY_RUNTIME", "system_settings", "payment.sepay.runtime");
        return getSePayRuntimeSettings();
    }

    @PostMapping("/integrations/sepay/reconciliation/run")
    @Operation(summary = "Run SePay reconciliation immediately")
    public ApiResponse<SePayReconciliationRunResponse> runSePayReconciliationNow() {
        var summary = sePayReconciliationService.reconcileNow();
        auditLogService.record(actorLabel(), "PAYMENT_RECONCILED", "sepay_reconciliation", "manual");
        return ApiResponse.success(
                summary.queryFailures() > 0 || summary.candidateFailures() > 0
                        ? "SePay reconciliation completed with some failures"
                        : "SePay reconciliation completed",
                new SePayReconciliationRunResponse(
                        summary.pendingOrders(),
                        summary.queriedOrders(),
                        summary.matchedCandidates(),
                        summary.queryFailures(),
                        summary.candidateFailures()));
    }

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
                settings.timeoutSeconds()
        );
        return ApiResponse.success("Assignment AI settings loaded", response);
    }

    @PutMapping("/ai/assignment-draft")
    @Transactional
    @Operation(summary = "Update assignment AI draft settings")
    public ApiResponse<AssignmentAiSettingsResponse> updateAssignmentAiSettings(
            @Valid @RequestBody AssignmentAiSettingsUpdateRequest request
    ) {
        UUID actor = currentUserId();
        settingsService.put(SettingKeys.ASSIGNMENT_AI_ENABLED, String.valueOf(Boolean.TRUE.equals(request.enabled())), false, actor);
        settingsService.put(SettingKeys.ASSIGNMENT_AI_PROVIDER, request.provider(), false, actor);
        putOptionalSecret(SettingKeys.ASSIGNMENT_AI_API_KEY, request.apiKey(), actor);
        settingsService.put(SettingKeys.ASSIGNMENT_AI_MODEL, request.model(), false, actor);
        settingsService.put(SettingKeys.ASSIGNMENT_AI_FALLBACK_MODEL, request.fallbackModel(), false, actor);
        settingsService.put(SettingKeys.ASSIGNMENT_AI_TIMEOUT_SECONDS, String.valueOf(request.timeoutSeconds()), false, actor);
        auditLogService.record(actorLabel(), "SETTINGS_UPDATE_ASSIGNMENT_AI", "system_settings", "assignment_ai");
        return getAssignmentAiSettings();
    }

    private SePayBankDisplaySettingsResponse toSePayBankDisplayResponse() {
        SePayBankDisplaySettings settings = settingsService.resolveSePayBankDisplaySettings();
        return new SePayBankDisplaySettingsResponse(
                settings.accountNumber(),
                settings.bankName(),
                settings.accountName(),
                settings.isConfigured());
    }

    private SePayRuntimeSettingsResponse toSePayRuntimeResponse() {
        SePayRuntimeSettings settings = settingsService.resolveSePayRuntimeSettings();
        return new SePayRuntimeSettingsResponse(
                settings.hasApiToken(),
                settings.hasWebhookSecret(),
                settingsService.hasValue(SettingKeys.SEPAY_API_TOKEN),
                settingsService.hasValue(SettingKeys.SEPAY_WEBHOOK_SECRET));
    }

    private void putOptionalText(String key, String value, UUID actor) {
        if (value == null) {
            return;
        }
        if (value.isBlank()) {
            settingsService.delete(key);
            return;
        }
        settingsService.put(key, value, false, actor);
    }

    private void putOptionalSecret(String key, String value, UUID actor) {
        if (value == null || SystemSettingsService.SECRET_PLACEHOLDER.equals(value)) {
            return;
        }
        if (value.isBlank()) {
            settingsService.delete(key);
            return;
        }
        settingsService.put(key, value, true, actor);
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
