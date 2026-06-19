package com.smartlearnly.backend.payment.sepay;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SePayWebhookSignatureVerifier {
    private static final String SIGNATURE_PREFIX = "sha256=";
    private static final long TIMESTAMP_TOLERANCE_SECONDS = 300;

    private final SePayProperties sePayProperties;
    private final Clock clock;

    @Autowired
    public SePayWebhookSignatureVerifier(SePayProperties sePayProperties) {
        this(sePayProperties, Clock.systemUTC());
    }

    SePayWebhookSignatureVerifier(SePayProperties sePayProperties, Clock clock) {
        this.sePayProperties = sePayProperties;
        this.clock = clock;
    }

    public long verify(byte[] rawBody, String signature, String timestampHeader) {
        String secret = sePayProperties.getWebhookSecret();
        if (secret == null || secret.isBlank()) {
            throw new BusinessException(
                    ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                    "SePay webhook verification is not configured"
            );
        }

        long timestamp = parseTimestamp(timestampHeader);
        validateTimestamp(timestamp);
        byte[] providedSignature = parseSignature(signature);
        byte[] expectedSignature = computeSignature(secret, timestamp, rawBody == null ? new byte[0] : rawBody);
        if (!MessageDigest.isEqual(expectedSignature, providedSignature)) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "SePay webhook signature is invalid");
        }
        return timestamp;
    }

    private long parseTimestamp(String timestampHeader) {
        if (timestampHeader == null || timestampHeader.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "SePay webhook timestamp is required");
        }
        try {
            return Long.parseLong(timestampHeader.trim());
        }
        catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "SePay webhook timestamp is invalid");
        }
    }

    private void validateTimestamp(long timestamp) {
        long now = clock.instant().getEpochSecond();
        if (timestamp < now - TIMESTAMP_TOLERANCE_SECONDS
                || timestamp > now + TIMESTAMP_TOLERANCE_SECONDS) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "SePay webhook timestamp is expired");
        }
    }

    private byte[] parseSignature(String signature) {
        if (signature == null || signature.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "SePay webhook signature is required");
        }
        String normalized = signature.trim();
        if (!normalized.startsWith(SIGNATURE_PREFIX)) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "SePay webhook signature is invalid");
        }
        String hex = normalized.substring(SIGNATURE_PREFIX.length());
        if (hex.length() != 64) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "SePay webhook signature is invalid");
        }
        try {
            return HexFormat.of().parseHex(hex);
        }
        catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "SePay webhook signature is invalid");
        }
    }

    private byte[] computeSignature(String secret, long timestamp, byte[] rawBody) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update((timestamp + ".").getBytes(StandardCharsets.UTF_8));
            return mac.doFinal(rawBody);
        }
        catch (Exception exception) {
            throw new BusinessException(
                    ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                    "SePay webhook verification is unavailable"
            );
        }
    }
}
