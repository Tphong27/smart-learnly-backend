package com.smartlearnly.backend.payment.sepay;

import java.math.BigDecimal;

public record SePayTransactionQuery(
        String q,
        String transferType,
        BigDecimal amountInMin,
        BigDecimal amountInMax,
        int perPage,
        String timestampFormat
) {
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
