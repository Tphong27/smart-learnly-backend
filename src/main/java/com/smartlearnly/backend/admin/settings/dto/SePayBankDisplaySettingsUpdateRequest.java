package com.smartlearnly.backend.admin.settings.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Update payload for SePay bank details rendered on checkout instructions.
 */
public record SePayBankDisplaySettingsUpdateRequest(
        @NotBlank(message = "SePay account number is required")
        @Size(max = 100, message = "SePay account number must be at most 100 characters")
        String accountNumber,

        @NotBlank(message = "SePay bank name is required")
        @Size(max = 100, message = "SePay bank name must be at most 100 characters")
        String bankName,

        @NotBlank(message = "SePay account name is required")
        @Size(max = 150, message = "SePay account name must be at most 150 characters")
        String accountName
) {
}
