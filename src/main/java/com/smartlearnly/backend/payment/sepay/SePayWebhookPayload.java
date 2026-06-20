package com.smartlearnly.backend.payment.sepay;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;

record SePayWebhookPayload(
        long id,
        String code,
        String content,
        String transferType,
        BigDecimal transferAmount,
        String accountNumber,
        String transactionDate,
        String referenceCode
) {
    static SePayWebhookPayload from(JsonNode root) {
        return new SePayWebhookPayload(
                root.get("id").longValue(),
                text(root, "code"),
                text(root, "content"),
                text(root, "transferType"),
                decimal(root, "transferAmount"),
                text(root, "accountNumber"),
                text(root, "transactionDate"),
                text(root, "referenceCode")
        );
    }

    private static String text(JsonNode root, String fieldName) {
        JsonNode value = root.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asText();
    }

    private static BigDecimal decimal(JsonNode root, String fieldName) {
        JsonNode value = root.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isNumber()) {
            return value.decimalValue();
        }
        if (value.isTextual()) {
            try {
                return new BigDecimal(value.asText());
            }
            catch (NumberFormatException exception) {
                return null;
            }
        }
        return null;
    }
}
