package com.smartlearnly.backend.payment.sepay;

import java.math.BigDecimal;

record SePayPaymentMatchCandidate(
        Long gatewayEventId,
        String code,
        String content,
        String transferType,
        BigDecimal transferAmount,
        String accountNumber,
        String transactionDate,
        String gatewayTransactionId
) {
    static SePayPaymentMatchCandidate fromWebhook(SePayWebhookPayload payload) {
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

    static SePayPaymentMatchCandidate fromReconciledTransaction(SePayTransactionCandidate candidate) {
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
