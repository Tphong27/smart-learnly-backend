package com.smartlearnly.backend.hls.controller;

import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.file.config.StorageProperties;
import com.smartlearnly.backend.file.service.CloudflareR2StorageClient;
import com.smartlearnly.backend.hls.entity.HlsLesson;
import com.smartlearnly.backend.hls.config.HlsProperties;
import com.smartlearnly.backend.hls.repository.HlsLessonRepository;
import com.smartlearnly.backend.hls.service.HlsAccessService;
import com.smartlearnly.backend.hls.service.HlsTokenService;
import com.smartlearnly.backend.hls.service.HlsTokenService.MasterTokenPayload;
import com.smartlearnly.backend.hls.service.HlsTokenService.SegmentTokenPayload;
import com.smartlearnly.backend.user.entity.UserAccount;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@RestController
@RequestMapping("/api/v1/hls")
@RequiredArgsConstructor
@Tag(name = "HLS Video", description = "HLS video streaming with secure token-based access")
public class HlsController {

    private static final Pattern QUALITY_PATTERN = Pattern.compile("^[a-zA-Z0-9]+$");
    private static final Pattern SEGMENT_PATTERN = Pattern.compile("^[a-zA-Z0-9_\\-]+\\.ts$");
    private static final Pattern R2_BASE_PATTERN = Pattern.compile("^hls/[a-zA-Z0-9_][a-zA-Z0-9_/\\-]*$");
    private final HlsTokenService tokenService;
    private final HlsAccessService accessService;
    private final HlsLessonRepository hlsLessonRepository;
    private final CloudflareR2StorageClient r2Storage;
    private final CurrentUserService currentUserService;
    private final StorageProperties storageProperties;
    private final HlsProperties hlsProperties;

    @GetMapping("/playlist/{lessonId}")
    @Operation(summary = "Get HLS master playlist with rewritten URLs")
    public ResponseEntity<String> getPlaylist(
            @PathVariable UUID lessonId,
            @RequestParam("token") String token) {

        // Step 1: Validate lesson exists
        HlsLesson hlsLesson = hlsLessonRepository.findByLessonId(lessonId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "HLS lesson not found"));

        if (!hlsLesson.isReady()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "HLS content not ready");
        }

        // Step 2: Validate master token
        Optional<MasterTokenPayload> payloadOpt = tokenService.validateMasterToken(token, lessonId);
        if (payloadOpt.isEmpty()) {
            return ResponseEntity.status(403)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("Invalid or expired token");
        }
        MasterTokenPayload payload = payloadOpt.get();

        // Step 3: Check user access
        UUID userId = UUID.fromString(payload.uid());
        if (!accessService.checkUserAccess(userId, lessonId)) {
            return ResponseEntity.status(403)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("Access denied");
        }

        // Step 4: Fetch master playlist from R2
        String r2Base = hlsLesson.getR2BasePath();
        String masterPlaylistKey = r2Base + "/master.m3u8";

        try {
            byte[] playlistBytes = r2Storage.getObject(hlsBucket(), masterPlaylistKey);
            String masterPlaylist = new String(playlistBytes, StandardCharsets.UTF_8);

            String rewrittenPlaylist = rewriteMasterPlaylist(masterPlaylist, lessonId, token);

            log.debug("Serving master playlist for lesson={}, user={}", lessonId, userId);

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/vnd.apple.mpegurl"))
                    .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                    .body(rewrittenPlaylist);

        } catch (BusinessException e) {
            log.warn("Failed to fetch master playlist: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/variant/{lessonId}/{quality}")
    @Operation(summary = "Get HLS variant playlist with segment URLs")
    public ResponseEntity<String> getVariant(
            @PathVariable UUID lessonId,
            @PathVariable String quality,
            @RequestParam("token") String token) {

        // Validate quality format
        if (!QUALITY_PATTERN.matcher(quality).matches()) {
            return ResponseEntity.status(403)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("Invalid quality parameter");
        }

        // The master token remains in the rewritten variant URL.
        Optional<MasterTokenPayload> payloadOpt = tokenService.validateMasterToken(token, lessonId);
        if (payloadOpt.isEmpty()) {
            return ResponseEntity.status(403)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("Invalid or expired token");
        }
        MasterTokenPayload payload = payloadOpt.get();
        UUID userId = UUID.fromString(payload.uid());

        if (!accessService.checkUserAccess(userId, lessonId)) {
            return ResponseEntity.status(403)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("Access denied");
        }

        // Fetch variant playlist
        HlsLesson hlsLesson = hlsLessonRepository.findByLessonId(lessonId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "HLS lesson not found"));

        String r2Base = hlsLesson.getR2BasePath();
        String variantPlaylistKey = r2Base + "/" + quality + "/playlist.m3u8";

        try {
            byte[] playlistBytes = r2Storage.getObject(hlsBucket(), variantPlaylistKey);
            String variantPlaylist = new String(playlistBytes, StandardCharsets.UTF_8);

            // Rewrite segment URLs with per-segment tokens
            String rewrittenPlaylist = rewriteVariantPlaylist(
                    variantPlaylist,
                    lessonId,
                    userId,
                    quality,
                    r2Base,
                    token
            );

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/vnd.apple.mpegurl"))
                    .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                    .body(rewrittenPlaylist);

        } catch (BusinessException e) {
            log.warn("Failed to fetch variant playlist: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/key/{lessonId}")
    @Operation(summary = "Get AES-128 encryption key")
    public ResponseEntity<byte[]> getKey(
            @PathVariable UUID lessonId,
            @RequestParam(value = "token", required = false) String token,
            @RequestHeader(value = "X-HLS-Token", required = false) String headerToken) {

        String actualToken = token != null ? token : headerToken;

        // Validate token and access
        Optional<MasterTokenPayload> payloadOpt = tokenService.validateMasterToken(actualToken, lessonId);
        if (payloadOpt.isEmpty()) {
            return ResponseEntity.status(403).build();
        }

        UUID userId = UUID.fromString(payloadOpt.get().uid());
        if (!accessService.checkUserAccess(userId, lessonId)) {
            return ResponseEntity.status(403).build();
        }

        // Fetch encryption key from R2 (server-to-server, no presigning)
        HlsLesson hlsLesson = hlsLessonRepository.findByLessonId(lessonId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "HLS lesson not found"));

        String keyPath = hlsLesson.getR2BasePath() + "/enc.key";

        try {
            byte[] keyBytes = r2Storage.getObject(hlsBucket(), keyPath);

            // Validate key is exactly 16 bytes (AES-128)
            if (keyBytes.length != 16) {
                log.error("Invalid AES key length for lesson={}: {}", lessonId, keyBytes.length);
                return ResponseEntity.status(500).build();
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_LENGTH, "16")
                    .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                    .body(keyBytes);

        } catch (BusinessException e) {
            log.warn("Failed to fetch encryption key: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/segment/{lessonId}/{quality}/{segmentName}")
    @Operation(summary = "Get HLS segment directly from R2 (validated by segment token)")
    public ResponseEntity<byte[]> getSegment(
            @PathVariable UUID lessonId,
            @PathVariable String quality,
            @PathVariable String segmentName,
            @RequestParam("t") String token) {

        // Validate quality and segment name
        if (!QUALITY_PATTERN.matcher(quality).matches()) {
            return ResponseEntity.status(403).build();
        }
        if (!SEGMENT_PATTERN.matcher(segmentName).matches()) {
            return ResponseEntity.status(403).build();
        }

        // Validate segment token
        Optional<SegmentTokenPayload> segPayloadOpt = tokenService.validateSegmentToken(token);
        if (segPayloadOpt.isEmpty()) {
            return ResponseEntity.status(403).build();
        }

        SegmentTokenPayload segPayload = segPayloadOpt.get();

        // Field-by-field validation
        if (!segPayload.lessonId().equals(lessonId) ||
                !segPayload.quality().equals(quality) ||
                !segPayload.segmentName().equals(segmentName)) {
            log.warn("Segment token mismatch: URL params don't match token");
            return ResponseEntity.status(403).build();
        }

        // Validate R2 base path
        if (!R2_BASE_PATTERN.matcher(segPayload.r2Base()).matches()) {
            log.warn("Invalid R2 base path in segment token: {}", segPayload.r2Base());
            return ResponseEntity.status(403).build();
        }

        // Check user access
        if (!accessService.checkUserAccess(segPayload.userId(), lessonId)) {
            return ResponseEntity.status(403).build();
        }

        // Fetch segment from R2
        String segmentKey = segPayload.r2Base() + "/" + quality + "/" + segmentName;

        try {
            byte[] segmentData = r2Storage.getObject(hlsBucket(), segmentKey);

            log.debug("Serving segment: lesson={}, quality={}, segment={}, size={}",
                    lessonId, quality, segmentName, segmentData.length);

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("video/MP2T"))
                    .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(segmentData.length))
                    .header(HttpHeaders.CACHE_CONTROL, "private, no-store, max-age=0")
                    .body(segmentData);

        } catch (BusinessException e) {
            log.warn("Failed to fetch segment: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/refresh_token")
    @Operation(summary = "Refresh HLS master token before expiry")
    public ResponseEntity<ApiResponse<TokenRefreshResponse>> refreshToken(
            @RequestBody TokenRefreshRequest request,
            HttpServletRequest httpRequest) {

        // Require authentication
        UserAccount user;
        try {
            user = currentUserService.requireAuthenticatedUser();
        } catch (BusinessException e) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.error("Authentication required"));
        }

        // Re-verify access
        if (!accessService.checkUserAccess(user.getId(), request.lessonId())) {
            return ResponseEntity.status(403)
                    .body(ApiResponse.error("Access denied"));
        }

        // Generate new token
        String sessionId = httpRequest.getSession().getId();
        String fingerprint = request.fingerprint();
        String ipAddress = getClientIpAddress(httpRequest);

        String newToken = tokenService.generateMasterToken(
                user.getId(),
                request.lessonId(),
                sessionId,
                fingerprint,
                ipAddress
        );

        int expiresIn = hlsProperties.getMasterTokenTtlSeconds();
        Instant expiresAt = Instant.now().plusSeconds(expiresIn);

        TokenRefreshResponse response = new TokenRefreshResponse(
                newToken,
                expiresAt.getEpochSecond(),
                expiresIn,
                "/api/v1/hls/playlist/" + request.lessonId() + "?token=" + newToken
        );

        log.debug("Token refreshed for user={}, lesson={}", user.getId(), request.lessonId());

        return ResponseEntity.ok(ApiResponse.success("Token refreshed", response));
    }

    @GetMapping("/free_video/{lessonId}")
    @Operation(summary = "Get free preview video (no auth required)")
    public ResponseEntity<Void> getFreeVideo(@PathVariable UUID lessonId) {
        // Check preview access
        if (!accessService.checkPreviewAccess(lessonId)) {
            return ResponseEntity.status(403).build();
        }

        // Redirect to token flow for cloudflare storage
        return ResponseEntity.status(302)
                .location(URI.create("/api/v1/hls/token/" + lessonId))
                .build();
    }

    // ==================== Helper Methods ====================

    private String rewriteMasterPlaylist(String masterPlaylist, UUID lessonId, String masterToken) {
        StringBuilder rewritten = new StringBuilder();
        String[] lines = masterPlaylist.split("\n");

        for (String rawLine : lines) {
            String line = rawLine.strip();
            if (line.startsWith("#")) {
                // Pass through comments and directives
                rewritten.append(line).append("\n");
            } else if (!line.isBlank()) {
                // Rewrite variant playlist URLs
                String quality = line.replaceFirst("/playlist\\.m3u8$", "");
                String rewrittenUrl = "/api/v1/hls/variant/" + lessonId + "/" + quality
                        + "?token=" + masterToken;
                rewritten.append(rewrittenUrl).append("\n");
            }
        }

        return rewritten.toString();
    }

    private String rewriteVariantPlaylist(String variantPlaylist, UUID lessonId,
                                          UUID userId, String quality, String r2Base,
                                          String masterToken) {
        StringBuilder rewritten = new StringBuilder();
        String[] lines = variantPlaylist.split("\n");

        for (String rawLine : lines) {
            String line = rawLine.strip();
            if (line.startsWith("#EXT-X-KEY")) {
                // Rewrite key URI
                String rewrittenLine = line.replaceAll(
                        "URI=\"[^\"]*\"",
                        "URI=\"/api/v1/hls/key/" + lessonId + "?token=" + masterToken + "\""
                );
                rewritten.append(rewrittenLine).append("\n");
            } else if (line.startsWith("#")) {
                // Pass through other directives
                rewritten.append(line).append("\n");
            } else if (!line.isBlank() && line.endsWith(".ts")) {
                // Generate per-segment token
                String segmentToken = tokenService.generateSegmentToken(
                        userId, lessonId, quality, line, r2Base
                );
                String rewrittenUrl = "/api/v1/hls/segment/" + lessonId + "/" + quality + "/" + line + "?t=" + segmentToken;
                rewritten.append(rewrittenUrl).append("\n");
            } else if (!line.isBlank()) {
                // Pass through other URLs
                rewritten.append(line).append("\n");
            }
        }

        return rewritten.toString();
    }

    private String hlsBucket() {
        String configuredBucket = hlsProperties.getOutputBucket();
        return configuredBucket == null || configuredBucket.isBlank()
                ? storageProperties.getLessonMaterialBucket()
                : configuredBucket;
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    // ==================== DTO Classes ====================

    public record TokenRefreshRequest(
            UUID lessonId,
            String fingerprint
    ) {}

    public record TokenRefreshResponse(
            String token,
            long expiresAt,
            int expiresIn,
            String playlistUrl
    ) {}
}
