package com.smartlearnly.backend.payment.sepay.service;

import com.smartlearnly.backend.payment.sepay.dto.SePayWebhookPayload;
import com.smartlearnly.backend.payment.sepay.repository.SePayWebhookEventRepository;
import com.smartlearnly.backend.payment.sepay.webhook.SePayWebhookPayloadParser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SePayWebhookService {
    private final SePayWebhookSignatureVerifier signatureVerifier;
    private final SePayWebhookEventRepository webhookEventRepository;
    private final SePayPaymentMatchingService paymentMatchingService;
    private final SePayWebhookPayloadParser payloadParser;

    @Transactional
    public void receive(byte[] rawBody, String signature, String timestampHeader) {
        long eventTimestamp = signatureVerifier.verify(rawBody, signature, timestampHeader);
        String payload = payloadParser.toPayloadString(rawBody);
        var root = payloadParser.parse(rawBody);
        long gatewayEventId = payloadParser.extractEventId(root);
        boolean inserted = webhookEventRepository.saveReceivedEvent(gatewayEventId, signature, eventTimestamp, payload);
        if (!inserted && shouldSkipDuplicate(gatewayEventId)) {
            return;
        }
        paymentMatchingService.process(SePayWebhookPayload.from(root));
    }

    private boolean shouldSkipDuplicate(long gatewayEventId) {
        String status = webhookEventRepository.findProcessingStatusByGatewayEventIdForUpdate(gatewayEventId)
                .orElse("FAILED");
        return "PROCESSED".equals(status)
                || "MISMATCHED".equals(status)
                || "DUPLICATE".equals(status);
    }
}
