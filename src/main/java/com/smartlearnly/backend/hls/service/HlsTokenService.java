package com.smartlearnly.backend.hls.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlearnly.backend.hls.config.HlsProperties;
import com.smartlearnly.backend.hls.repository.HlsActiveTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class HlsTokenService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final HlsActiveTokenRepository tokenRepository;
    private final HlsProperties hlsProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private String getTokenSecret() {
        return hlsProperties.getTokenSecret();
    }

    private String getR2Secret() {
        String secret = hlsProperties.getR2Secret();
        return secret != null ? secret : getTokenSecret();
    }

    // ==================== Token Generation ====================

    /**
     * Generates a master token for a user-lesson-session combination.
     * Format: base64url(payload_json).base64url(hmac_sha256(payload_b64, secret))
     */
    @Transactional
    public String generateMasterToken(UUID userId, UUID lessonId, String sessionId,
                                     String fingerprint, String ipAddress) {
        Instant now = Instant.now();
        int ttlSeconds = hlsProperties.getMasterTokenTtlSeconds();
        Instant expiresAt = now.plusSeconds(ttlSeconds);
        String jti = generateJti();

        MasterTokenPayload payload = new MasterTokenPayload(
                userId.toString(),
                lessonId.toString(),
                sessionId,
                fingerprint,
                expiresAt.getEpochSecond(),
                now.getEpochSecond(),
                jti
        );

        String payloadB64 = encodePayload(payload);
        String signature = computeHmac(payloadB64, getTokenSecret());
        String token = payloadB64 + "." + signature;

        // Store in revocation list
        String tokenHash = sha256(token);
        tokenRepository.upsertToken(
                userId, lessonId, sessionId,
                tokenHash,
                fingerprint,
                hashIpAddress(ipAddress),
                expiresAt
        );

        log.debug("Generated master token for user={}, lesson={}, session={}",
                userId, lessonId, sessionId);

        return token;
    }

    /**
     * Generates an R2 token for cloudflare storage access.
     * Format: base64("{timestamp}:{user_id}:{hmac_sha256(lid:uid:ts, secret)}")
     */
    public String generateR2Token(UUID lessonId, UUID userId) {
        Instant now = Instant.now();
        long timestamp = now.getEpochSecond();

        String dataToSign = lessonId + ":" + userId + ":" + timestamp;
        String signature = computeHmacHex(dataToSign, getR2Secret());

        String tokenData = timestamp + ":" + userId + ":" + signature;
        return Base64.getEncoder().encodeToString(tokenData.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generates a per-segment token for short-lived segment access.
     * Format: base64url("seg|{uid}|{lid}|{q}|{seg}|{r2_base}|{exp}").base64url(hmac)
     */
    public String generateSegmentToken(UUID userId, UUID lessonId, String quality,
                                       String segmentName, String r2Base) {
        int ttlSeconds = hlsProperties.getSegmentTokenTtlSeconds();
        Instant expiresAt = Instant.now().plusSeconds(ttlSeconds);
        long exp = expiresAt.getEpochSecond();

        String payload = String.format("seg|%s|%s|%s|%s|%s|%d",
                userId.toString(), lessonId.toString(), quality, segmentName, r2Base, exp);

        String payloadB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(
                payload.getBytes(StandardCharsets.UTF_8));
        String signature = computeHmac(payloadB64, getTokenSecret());

        return payloadB64 + "." + signature;
    }

    // ==================== Token Validation ====================

    /**
     * Validates a master token: signature, expiry, lesson_id match, and active session.
     */
    @Transactional(readOnly = true)
    public Optional<MasterTokenPayload> validateMasterToken(String token, UUID lessonId) {
        if (token == null || token.isBlank()) {
            log.warn("Empty token provided");
            return Optional.empty();
        }

        String[] parts = token.split("\\.");
        if (parts.length != 2) {
            log.warn("Invalid token format");
            return Optional.empty();
        }

        String payloadB64 = parts[0];
        String providedSignature = parts[1];

        // Verify signature using constant-time comparison
        String expectedSignature = computeHmac(payloadB64, getTokenSecret());
        if (!MessageDigest.isEqual(
                providedSignature.getBytes(StandardCharsets.UTF_8),
                expectedSignature.getBytes(StandardCharsets.UTF_8))) {
            log.warn("Token signature mismatch");
            return Optional.empty();
        }

        // Decode and parse payload
        MasterTokenPayload payload;
        try {
            String payloadJson = new String(Base64.getUrlDecoder().decode(payloadB64), StandardCharsets.UTF_8);
            payload = objectMapper.readValue(payloadJson, MasterTokenPayload.class);
        } catch (Exception e) {
            log.warn("Failed to decode token payload: {}", e.getMessage());
            return Optional.empty();
        }

        // Check expiry
        if (payload.exp() < Instant.now().getEpochSecond()) {
            log.warn("Token expired");
            return Optional.empty();
        }

        // Check lesson_id match
        if (!payload.lid().equals(lessonId.toString())) {
            log.warn("Token lesson_id mismatch: expected={}, got={}", lessonId, payload.lid());
            return Optional.empty();
        }

        // Check if token is still active in revocation list
        String tokenHash = sha256(token);
        if (!tokenRepository.existsByUserIdAndLessonIdAndSessionIdAndTokenHash(
                UUID.fromString(payload.uid()),
                UUID.fromString(payload.lid()),
                payload.sid(),
                tokenHash)) {
            log.warn("Token has been revoked or replaced");
            return Optional.empty();
        }

        return Optional.of(payload);
    }

    /**
     * Validates an R2 token for cloudflare storage access.
     */
    public boolean validateR2Token(String token, UUID lessonId, UUID userId) {
        if (token == null || token.isBlank()) {
            return false;
        }

        try {
            String decoded = new String(Base64.getDecoder().decode(token), StandardCharsets.UTF_8);
            String[] parts = decoded.split(":");
            if (parts.length != 3) {
                return false;
            }

            long timestamp = Long.parseLong(parts[0]);
            UUID tokenUserId = UUID.fromString(parts[1]);
            String providedSig = parts[2];

            // Check expiry (TTL from config)
            int ttlSeconds = hlsProperties.getR2TokenTtlSeconds();
            if (Instant.now().getEpochSecond() - timestamp > ttlSeconds) {
                log.warn("R2 token expired");
                return false;
            }

            // Verify signature
            String dataToSign = lessonId + ":" + userId + ":" + timestamp;
            String expectedSig = computeHmacHex(dataToSign, getR2Secret());

            if (!MessageDigest.isEqual(
                    providedSig.getBytes(StandardCharsets.UTF_8),
                    expectedSig.getBytes(StandardCharsets.UTF_8))) {
                log.warn("R2 token signature mismatch");
                return false;
            }

            // Check user_id match
            if (!tokenUserId.equals(userId)) {
                log.warn("R2 token user_id mismatch");
                return false;
            }

            return true;
        } catch (Exception e) {
            log.warn("R2 token validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Validates a segment token and returns the parsed payload.
     */
    public Optional<SegmentTokenPayload> validateSegmentToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }

        String[] parts = token.split("\\.");
        if (parts.length != 2) {
            return Optional.empty();
        }

        String payloadB64 = parts[0];
        String providedSignature = parts[1];

        // Verify signature
        String expectedSignature = computeHmac(payloadB64, getTokenSecret());
        if (!MessageDigest.isEqual(
                providedSignature.getBytes(StandardCharsets.UTF_8),
                expectedSignature.getBytes(StandardCharsets.UTF_8))) {
            log.warn("Segment token signature mismatch");
            return Optional.empty();
        }

        // Decode payload
        try {
            String payloadJson = new String(
                    Base64.getUrlDecoder().decode(payloadB64), StandardCharsets.UTF_8);
            String[] fields = payloadJson.split("\\|");

            if (fields.length != 7 || !"seg".equals(fields[0])) {
                log.warn("Invalid segment token payload format");
                return Optional.empty();
            }

            long exp = Long.parseLong(fields[6]);

            // Check expiry
            if (exp < Instant.now().getEpochSecond()) {
                log.warn("Segment token expired");
                return Optional.empty();
            }

            return Optional.of(new SegmentTokenPayload(
                    UUID.fromString(fields[1]),  // uid
                    UUID.fromString(fields[2]),  // lid
                    fields[3],                   // quality
                    fields[4],                   // segmentName
                    fields[5],                   // r2Base
                    exp
            ));
        } catch (Exception e) {
            log.warn("Segment token validation failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    // ==================== Token Revocation ====================

    @Transactional
    public void revokeToken(UUID userId, UUID lessonId, String sessionId) {
        tokenRepository.deleteByUserIdAndLessonIdAndSessionId(userId, lessonId, sessionId);
        log.info("Revoked HLS token for user={}, lesson={}, session={}", userId, lessonId, sessionId);
    }

    @Transactional
    public int cleanupExpiredTokens() {
        int deleted = tokenRepository.deleteExpiredTokens(Instant.now());
        if (deleted > 0) {
            log.info("Cleaned up {} expired HLS tokens", deleted);
        }
        return deleted;
    }

    // ==================== Helper Methods ====================

    private String computeHmac(String data, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            mac.init(secretKeySpec);
            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hmacBytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute HMAC", e);
        }
    }

    private String computeHmacHex(String data, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            mac.init(secretKeySpec);
            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));

            StringBuilder hex = new StringBuilder();
            for (byte b : hmacBytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute HMAC hex", e);
        }
    }

    private String encodePayload(MasterTokenPayload payload) {
        try {
            Map<String, Object> map = Map.of(
                    "uid", payload.uid(),
                    "lid", payload.lid(),
                    "sid", payload.sid(),
                    "fp", payload.fp() != null ? payload.fp() : "",
                    "exp", payload.exp(),
                    "iat", payload.iat(),
                    "jti", payload.jti()
            );
            String json = objectMapper.writeValueAsString(map);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to encode payload", e);
        }
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private String hashIpAddress(String ip) {
        if (ip == null) {
            return null;
        }
        return sha256(ip).substring(0, 16); // Store partial hash for privacy
    }

    private String generateJti() {
        byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // ==================== Record Types ====================

    public record MasterTokenPayload(
            String uid,
            String lid,
            String sid,
            String fp,
            long exp,
            long iat,
            String jti
    ) {}

    public record SegmentTokenPayload(
            UUID userId,
            UUID lessonId,
            String quality,
            String segmentName,
            String r2Base,
            long exp
    ) {}
}
