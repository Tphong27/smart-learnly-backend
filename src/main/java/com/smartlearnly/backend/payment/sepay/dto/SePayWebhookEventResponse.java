package com.smartlearnly.backend.payment.sepay.dto;

import java.time.Instant;
import java.util.UUID;

public record SePayWebhookEventResponse(
        UUID id,
        long gatewayEventId,
        String processingStatus,
        String failureReason,
        Instant receivedAt,
        Instant processedAt
) {
}
