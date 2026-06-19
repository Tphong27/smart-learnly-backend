package com.smartlearnly.backend.payment.sepay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SePayWebhookSignatureVerifierTest {
    private static final String SECRET = "webhook-secret-test-value";
    private static final String API_TOKEN = "api-token-test-value";
    private static final long NOW = 1781863200L;
    private static final byte[] RAW_BODY =
            "{\"id\":92704,\"transferAmount\":5000000}".getBytes(StandardCharsets.UTF_8);

    private SePayProperties sePayProperties;
    private SePayWebhookSignatureVerifier verifier;

    @BeforeEach
    void setUp() {
        sePayProperties = new SePayProperties();
        sePayProperties.setWebhookSecret(SECRET);
        sePayProperties.setApiToken(API_TOKEN);
        verifier = new SePayWebhookSignatureVerifier(
                sePayProperties,
                Clock.fixed(Instant.ofEpochSecond(NOW), ZoneOffset.UTC)
        );
    }

    @Test
    void verifyShouldAcceptValidSignature() {
        String signature = signature(NOW, RAW_BODY, SECRET);

        long timestamp = verifier.verify(RAW_BODY, signature, Long.toString(NOW));

        assertThat(timestamp).isEqualTo(NOW);
    }

    @Test
    void verifyShouldRejectInvalidSignatureWithoutExposingSecrets() {
        String signature = signature(NOW, RAW_BODY, "other-secret");

        assertThatThrownBy(() -> verifier.verify(RAW_BODY, signature, Long.toString(NOW)))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.UNAUTHENTICATED);
                    assertThat(exception.getMessage())
                            .doesNotContain(SECRET)
                            .doesNotContain(API_TOKEN);
                });
    }

    @Test
    void verifyShouldRejectMissingSignatureWithoutExposingSecrets() {
        assertThatThrownBy(() -> verifier.verify(RAW_BODY, null, Long.toString(NOW)))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.UNAUTHENTICATED);
                    assertThat(exception.getMessage())
                            .doesNotContain(SECRET)
                            .doesNotContain(API_TOKEN);
                });
    }

    @Test
    void verifyShouldRejectExpiredTimestamp() {
        long expiredTimestamp = NOW - 301;
        String signature = signature(expiredTimestamp, RAW_BODY, SECRET);

        assertThatThrownBy(() -> verifier.verify(RAW_BODY, signature, Long.toString(expiredTimestamp)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.UNAUTHENTICATED));
    }

    @Test
    void verifyShouldRejectMissingSecretWithoutExposingToken() {
        sePayProperties.setWebhookSecret("");
        String signature = signature(NOW, RAW_BODY, SECRET);

        assertThatThrownBy(() -> verifier.verify(RAW_BODY, signature, Long.toString(NOW)))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
                    assertThat(exception.getMessage())
                            .doesNotContain(SECRET)
                            .doesNotContain(API_TOKEN);
                });
    }

    private String signature(long timestamp, byte[] rawBody, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update((timestamp + ".").getBytes(StandardCharsets.UTF_8));
            return "sha256=" + HexFormat.of().formatHex(mac.doFinal(rawBody));
        }
        catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
