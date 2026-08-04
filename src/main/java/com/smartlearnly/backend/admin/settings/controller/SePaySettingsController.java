package com.smartlearnly.backend.admin.settings.controller;

import com.smartlearnly.backend.admin.settings.dto.SePayBankDisplaySettingsResponse;
import com.smartlearnly.backend.admin.settings.dto.SePayBankDisplaySettingsUpdateRequest;
import com.smartlearnly.backend.admin.settings.dto.SePayReconciliationRunResponse;
import com.smartlearnly.backend.admin.settings.dto.SePayRuntimeSettingsResponse;
import com.smartlearnly.backend.admin.settings.dto.SePayRuntimeSettingsUpdateRequest;
import com.smartlearnly.backend.admin.settings.service.SettingKeys;
import com.smartlearnly.backend.admin.settings.service.SystemSettingsService;
import com.smartlearnly.backend.admin.settings.service.SystemSettingsService.SePayBankDisplaySettings;
import com.smartlearnly.backend.admin.settings.service.SystemSettingsService.SePayRuntimeSettings;
import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.common.audit.AuditLogService;
import com.smartlearnly.backend.payment.sepay.service.SePayReconciliationService;
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
@Tag(name = "Admin System Settings", description = "Admin-only SePay configuration and reconciliation.")
public class SePaySettingsController {
    private final SystemSettingsService settingsService;
    private final AuditLogService auditLogService;
    private final AdminSettingsSupport support;
    private final SePayReconciliationService sePayReconciliationService;

    // Trả thông tin ngân hàng đang hiển thị trên hướng dẫn thanh toán SePay.
    @GetMapping("/integrations/sepay/bank-display")
    @Operation(summary = "Get current SePay bank display settings")
    public ApiResponse<SePayBankDisplaySettingsResponse> getSePayBankDisplaySettings() {
        return ApiResponse.success("SePay bank display settings loaded", toBankDisplayResponse());
    }

    // Lưu thông tin tài khoản nhận tiền SePay và ghi audit thay đổi.
    @PutMapping("/integrations/sepay/bank-display")
    @Transactional
    @Operation(summary = "Update SePay bank display settings")
    public ApiResponse<SePayBankDisplaySettingsResponse> updateSePayBankDisplaySettings(
            @Valid @RequestBody SePayBankDisplaySettingsUpdateRequest request) {
        UUID actor = support.currentUserId();
        settingsService.put(SettingKeys.SEPAY_ACCOUNT_NUMBER, request.accountNumber().trim(), false, actor);
        settingsService.put(SettingKeys.SEPAY_BANK_NAME, request.bankName().trim(), false, actor);
        settingsService.put(SettingKeys.SEPAY_ACCOUNT_NAME, request.accountName().trim(), false, actor);
        auditLogService.record(
                support.actorLabel(),
                "SETTINGS_UPDATE_SEPAY_BANK_DISPLAY",
                "system_settings",
                "payment.sepay.bank_display");
        return getSePayBankDisplaySettings();
    }

    // Trả trạng thái API token và webhook secret mà không lộ giá trị thật.
    @GetMapping("/integrations/sepay/runtime")
    @Operation(summary = "Get current SePay runtime secret settings (secret masked)")
    public ApiResponse<SePayRuntimeSettingsResponse> getSePayRuntimeSettings() {
        return ApiResponse.success("SePay runtime settings loaded", toRuntimeResponse());
    }

    // Cập nhật secret runtime SePay khi request gửi giá trị mới và ghi audit.
    @PutMapping("/integrations/sepay/runtime")
    @Transactional
    @Operation(summary = "Update SePay runtime secret settings")
    public ApiResponse<SePayRuntimeSettingsResponse> updateSePayRuntimeSettings(
            @Valid @RequestBody SePayRuntimeSettingsUpdateRequest request) {
        UUID actor = support.currentUserId();
        support.putOptionalSecret(SettingKeys.SEPAY_API_TOKEN, request.apiToken(), actor);
        support.putOptionalSecret(SettingKeys.SEPAY_WEBHOOK_SECRET, request.webhookSecret(), actor);
        auditLogService.record(
                support.actorLabel(),
                "SETTINGS_UPDATE_SEPAY_RUNTIME",
                "system_settings",
                "payment.sepay.runtime");
        return getSePayRuntimeSettings();
    }

    // Chạy đối soát SePay thủ công và trả số lượng đơn/giao dịch đã xử lý.
    @PostMapping("/integrations/sepay/reconciliation/run")
    @Operation(summary = "Run SePay reconciliation immediately")
    public ApiResponse<SePayReconciliationRunResponse> runSePayReconciliationNow() {
        var summary = sePayReconciliationService.reconcileNow();
        auditLogService.record(support.actorLabel(), "PAYMENT_RECONCILED", "sepay_reconciliation", "manual");
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

    // Chuyển cấu hình tài khoản SePay nội bộ thành response quản trị.
    private SePayBankDisplaySettingsResponse toBankDisplayResponse() {
        SePayBankDisplaySettings settings = settingsService.resolveSePayBankDisplaySettings();
        return new SePayBankDisplaySettingsResponse(
                settings.accountNumber(), settings.bankName(), settings.accountName(), settings.isConfigured());
    }

    // Chuyển secret SePay thành các cờ trạng thái an toàn cho response quản trị.
    private SePayRuntimeSettingsResponse toRuntimeResponse() {
        SePayRuntimeSettings settings = settingsService.resolveSePayRuntimeSettings();
        return new SePayRuntimeSettingsResponse(
                settings.hasApiToken(),
                settings.hasWebhookSecret(),
                settingsService.hasValue(SettingKeys.SEPAY_API_TOKEN),
                settingsService.hasValue(SettingKeys.SEPAY_WEBHOOK_SECRET));
    }
}
