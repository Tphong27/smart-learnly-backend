package com.smartlearnly.backend.hls.controller;

import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.hls.service.HlsAccessService;
import com.smartlearnly.backend.hls.service.HlsTokenService;
import com.smartlearnly.backend.hls.config.HlsProperties;
import com.smartlearnly.backend.user.entity.UserAccount;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/hls")
@RequiredArgsConstructor
@Tag(name = "HLS Token", description = "HLS token generation for video access")
public class HlsTokenController {

    private final HlsTokenService tokenService;
    private final HlsAccessService accessService;
    private final CurrentUserService currentUserService;
    private final HlsProperties hlsProperties;

    @GetMapping("/token/{lessonId}")
    @Operation(summary = "Get HLS master token for a lesson")
    public ResponseEntity<ApiResponse<HlsTokenResponse>> getToken(
            @PathVariable UUID lessonId,
            @RequestParam(value = "fingerprint", required = false) String fingerprint,
            HttpServletRequest request) {

        // Get user (may be null for guest/preview)
        UUID userId = null;
        try {
            UserAccount user = currentUserService.requireAuthenticatedUser();
            userId = user.getId();
        } catch (BusinessException e) {
            // Guest access - only allowed for preview lessons
            if (!accessService.checkPreviewAccess(lessonId)) {
                return ResponseEntity.status(401)
                        .body(ApiResponse.error("Authentication required for this lesson"));
            }
        }

        // Check access
        if (userId != null && !accessService.checkUserAccess(userId, lessonId)) {
            return ResponseEntity.status(403)
                    .body(ApiResponse.error("You do not have access to this lesson"));
        }

        // For guests, check preview access
        if (userId == null && !accessService.checkPreviewAccess(lessonId)) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.error("Authentication required for this lesson"));
        }

        // Generate master token
        HttpSession session = request.getSession(true);
        String sessionId = session.getId();
        String ipAddress = getClientIpAddress(request);

        String token = tokenService.generateMasterToken(
                userId != null ? userId : UUID.randomUUID(), // Use random UUID for guests
                lessonId,
                sessionId,
                fingerprint,
                ipAddress
        );

        int expiresIn = hlsProperties.getMasterTokenTtlSeconds();
        Instant expiresAt = Instant.now().plusSeconds(expiresIn);

        String playlistUrl = "/api/v1/hls/playlist/" + lessonId + "?token=" + token;

        HlsTokenResponse response = new HlsTokenResponse(
                token,
                playlistUrl,
                expiresAt.getEpochSecond(),
                expiresIn,
                sessionId
        );

        log.info("HLS token generated for lesson={}, user={}, session={}",
                lessonId, userId, sessionId);

        return ResponseEntity.ok(ApiResponse.success("Token generated", response));
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    public record HlsTokenResponse(
            String token,
            String playlistUrl,
            long expiresAt,
            int expiresIn,
            String sessionId
    ) {}
}
