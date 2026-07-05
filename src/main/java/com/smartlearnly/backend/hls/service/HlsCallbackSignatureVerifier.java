package com.smartlearnly.backend.hls.service;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.hls.config.HlsProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
@Component
public class HlsCallbackSignatureVerifier {
    private final HlsProperties properties;
    private final Clock clock;
    @Autowired
    public HlsCallbackSignatureVerifier(HlsProperties properties) { this(properties, Clock.systemUTC()); }
    HlsCallbackSignatureVerifier(HlsProperties properties, Clock clock) {
        this.properties = properties; this.clock = clock;
    }
    public void verify(byte[] body, String timestampHeader, String signatureHeader) {
        String secret = properties.getCallbackSecret();
        if (secret == null || secret.length() < 32) throw unavailable();
        long timestamp;
        try { timestamp = Long.parseLong(timestampHeader == null ? "" : timestampHeader.trim()); }
        catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "HLS callback timestamp is invalid");
        }
        int tolerance = properties.getCallbackTimestampToleranceSeconds();
        long now = clock.instant().getEpochSecond();
        if (tolerance < 30 || tolerance > 900 || timestamp < now - tolerance || timestamp > now + tolerance)
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "HLS callback timestamp is expired");
        String normalized = signatureHeader == null ? "" : signatureHeader.trim();
        if (normalized.startsWith("sha256=")) normalized = normalized.substring(7);
        byte[] provided;
        try {
            if (normalized.length() != 64) throw new IllegalArgumentException();
            provided = HexFormat.of().parseHex(normalized);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "HLS callback signature is invalid");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update((timestamp + ".").getBytes(StandardCharsets.UTF_8));
            if (!MessageDigest.isEqual(mac.doFinal(body == null ? new byte[0] : body), provided))
                throw new BusinessException(ErrorCode.UNAUTHENTICATED, "HLS callback signature is invalid");
        } catch (BusinessException exception) { throw exception; }
        catch (Exception exception) { throw unavailable(); }
    }
    private BusinessException unavailable() {
        return new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                "HLS callback verification is not configured");
    }
}
