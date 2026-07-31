package com.smartlearnly.backend.payment.sepay;

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
