package com.smartlearnly.backend.payment.sepay.dto;

public record SePayWebhookAck(boolean success) {
    // Tạo phản hồi thành công ổn định để SePay không gửi lại webhook đã nhận.
    public static SePayWebhookAck accepted() {
        return new SePayWebhookAck(true);
    }
}
