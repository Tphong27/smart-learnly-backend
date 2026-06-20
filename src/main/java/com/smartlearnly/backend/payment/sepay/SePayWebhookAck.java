package com.smartlearnly.backend.payment.sepay;

public record SePayWebhookAck(boolean success) {
    static SePayWebhookAck accepted() {
        return new SePayWebhookAck(true);
    }
}
