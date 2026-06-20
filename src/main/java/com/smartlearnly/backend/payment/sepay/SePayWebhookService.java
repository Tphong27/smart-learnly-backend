package com.smartlearnly.backend.payment.sepay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SePayWebhookService {
    private final SePayWebhookSignatureVerifier signatureVerifier;
    private final SePayWebhookEventRepository webhookEventRepository;
    private final SePayPaymentMatchingService paymentMatchingService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public void receive(byte[] rawBody, String signature, String timestampHeader) {
        long eventTimestamp = signatureVerifier.verify(rawBody, signature, timestampHeader);
        String payload = new String(rawBody == null ? new byte[0] : rawBody, StandardCharsets.UTF_8);
        JsonNode root = parseRoot(rawBody);
        long gatewayEventId = extractGatewayEventId(root);
        boolean inserted = webhookEventRepository.saveReceivedEvent(gatewayEventId, signature, eventTimestamp, payload);
        if (!inserted && shouldSkipDuplicate(gatewayEventId)) {
            return;
        }
        paymentMatchingService.process(SePayWebhookPayload.from(root));
    }

    private JsonNode parseRoot(byte[] rawBody) {
        try {
            return objectMapper.readTree(rawBody == null ? new byte[0] : rawBody);
        }
        catch (IOException exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "SePay webhook payload is invalid");
        }
    }

    private long extractGatewayEventId(JsonNode root) {
        JsonNode id = root == null ? null : root.get("id");
        if (id == null || !id.isIntegralNumber()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "SePay webhook event id is required");
        }
        return id.longValue();
    }

    private boolean shouldSkipDuplicate(long gatewayEventId) {
        String status = webhookEventRepository.findProcessingStatusByGatewayEventIdForUpdate(gatewayEventId)
                .orElse("FAILED");
        return "PROCESSED".equals(status)
                || "MISMATCHED".equals(status)
                || "DUPLICATE".equals(status);
    }
}
