package com.smartlearnly.backend.payment.sepay.dto;

import java.math.BigDecimal;

public record SePayPaymentMatchCandidate(
        Long gatewayEventId,
        String code,
        String content,
        String transferType,
        BigDecimal transferAmount,
        String accountNumber,
        String transactionDate,
        String gatewayTransactionId
) {
    // Chuẩn hóa một webhook thành ứng viên giao dịch để dùng chung luồng matching.
    public static SePayPaymentMatchCandidate fromWebhook(SePayWebhookPayload payload) {
        return new SePayPaymentMatchCandidate(
                payload.id(),
                payload.code(),
                payload.content(),
                payload.transferType(),
                payload.transferAmount(),
                payload.accountNumber(),
                payload.transactionDate(),
                payload.referenceCode()
        );
    }

    // Chuẩn hóa giao dịch lấy từ API đối soát thành ứng viên matching.
    public static SePayPaymentMatchCandidate fromReconciledTransaction(SePayTransactionCandidate candidate) {
        return new SePayPaymentMatchCandidate(
                null,
                candidate.code(),
                candidate.transactionContent(),
                candidate.transferType(),
                candidate.amountIn(),
                candidate.accountNumber(),
                candidate.transactionDate(),
                candidate.gatewayTransactionId()
        );
    }
}
