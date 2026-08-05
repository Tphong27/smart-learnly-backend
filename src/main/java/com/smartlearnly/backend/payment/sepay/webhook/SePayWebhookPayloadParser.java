package com.smartlearnly.backend.payment.sepay.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import java.io.IOException;
import org.springframework.stereotype.Component;

/**
 * Parses and validates SePay webhook payloads.
 */
@Component
public class SePayWebhookPayloadParser {

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public SePayWebhookPayloadParser() {
        this.objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
    }

    /**
     * Parses raw bytes into a JsonNode.
     *
     * @param rawBody the raw webhook body
     * @return the parsed JSON
     * @throws BusinessException if parsing fails
     */
    public JsonNode parse(byte[] rawBody) {
        try {
            return objectMapper.readTree(rawBody == null ? new byte[0] : rawBody);
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "SePay webhook payload is invalid");
        }
    }

    /**
     * Extracts the gateway event ID from the payload.
     *
     * @param root the parsed JSON
     * @return the gateway event ID
     * @throws BusinessException if ID is missing or invalid
     */
    public long extractEventId(JsonNode root) {
        JsonNode id = root == null ? null : root.get("id");
        if (id == null || !id.isIntegralNumber()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "SePay webhook event id is required");
        }
        return id.longValue();
    }

    /**
     * Converts raw bytes to a string payload.
     *
     * @param rawBody the raw webhook body
     * @return the payload string
     */
    public String toPayloadString(byte[] rawBody) {
        if (rawBody == null) {
            return "";
        }
        return new String(rawBody, java.nio.charset.StandardCharsets.UTF_8);
    }
}
