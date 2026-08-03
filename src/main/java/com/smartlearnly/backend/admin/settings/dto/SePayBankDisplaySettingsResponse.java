package com.smartlearnly.backend.admin.settings.dto;

/**
 * SePay bank display settings shown to learners during checkout.
 */
public record SePayBankDisplaySettingsResponse(
        String accountNumber,
        String bankName,
        String accountName,
        boolean configured
) {
}
