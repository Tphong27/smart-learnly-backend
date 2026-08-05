package com.smartlearnly.backend.payment.sepay.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartlearnly.backend.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

class SePayWebhookPayloadParserTest {

    private final SePayWebhookPayloadParser parser = new SePayWebhookPayloadParser();

    @Test
    void parse_validJson_returnsRootNode() throws Exception {
        String json = "{\"id\": 123, \"amount\": 100000}";
        byte[] raw = json.getBytes();

        var result = parser.parse(raw);

        assertThat(result.has("id")).isTrue();
        assertThat(result.get("id").asLong()).isEqualTo(123);
    }

    @Test
    void parse_nullBody_returnsEmptyObject() {
        var result = parser.parse(null);

        assertThat(result.isEmpty()).isTrue();
    }

    @Test
    void parse_invalidJson_throwsException() {
        byte[] raw = "not json".getBytes();

        assertThatThrownBy(() -> parser.parse(raw))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalid");
    }

    @Test
    void extractEventId_validPayload_returnsId() throws Exception {
        String json = "{\"id\": 456789}";
        var root = parser.parse(json.getBytes());

        long result = parser.extractEventId(root);

        assertThat(result).isEqualTo(456789);
    }

    @Test
    void extractEventId_nullPayload_throwsException() {
        assertThatThrownBy(() -> parser.extractEventId(null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("required");
    }

    @Test
    void extractEventId_missingId_throwsException() throws Exception {
        String json = "{\"amount\": 100000}";
        var root = parser.parse(json.getBytes());

        assertThatThrownBy(() -> parser.extractEventId(root))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("required");
    }

    @Test
    void extractEventId_nonIntegralId_throwsException() throws Exception {
        String json = "{\"id\": \"abc\"}";
        var root = parser.parse(json.getBytes());

        assertThatThrownBy(() -> parser.extractEventId(root))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("required");
    }

    @Test
    void toPayloadString_validBytes_returnsString() {
        byte[] raw = "test payload".getBytes();

        String result = parser.toPayloadString(raw);

        assertThat(result).isEqualTo("test payload");
    }

    @Test
    void toPayloadString_nullBytes_returnsEmptyString() {
        String result = parser.toPayloadString(null);

        assertThat(result).isEmpty();
    }
}
