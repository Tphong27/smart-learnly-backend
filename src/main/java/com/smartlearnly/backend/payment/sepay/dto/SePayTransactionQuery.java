package com.smartlearnly.backend.payment.sepay.dto;

import java.math.BigDecimal;

public record SePayTransactionQuery(
        String q,
        String transferType,
        BigDecimal amountInMin,
        BigDecimal amountInMax,
        int perPage,
        String timestampFormat
) {
    // Tạo bộ lọc API SePay chuẩn để tìm giao dịch tiền vào đúng mã và số tiền của đơn.
    public static SePayTransactionQuery forPaymentCode(String paymentCode, BigDecimal amount) {
        return new SePayTransactionQuery(
                paymentCode,
                "in",
                amount,
                amount,
                20,
                "iso8601"
        );
    }
}
