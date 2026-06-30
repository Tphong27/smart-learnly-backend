package com.smartlearnly.backend.hls.controller;

import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.hls.config.HlsProperties;
import com.smartlearnly.backend.hls.service.HlsUploadService;
import com.smartlearnly.backend.hls.service.VideoProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/hls/upload")
@RequiredArgsConstructor
public class HlsUploadController {

    private final HlsUploadService hlsUploadService;
    private final VideoProcessingService videoProcessingService;
    private final HlsProperties hlsProperties;

    /**
     * Upload a video file for HLS processing.
     *
     * POST /api/v1/hls/upload
     * Body: multipart/form-data with 'video' file and 'lessonId' field
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'TMO', 'SME')")
    public ResponseEntity<ApiResponse<HlsUploadService.UploadResponse>> uploadVideo(
            @RequestParam("lessonId") UUID lessonId,
            @RequestParam(value = "video", required = false) MultipartFile videoFile,
            @RequestParam(value = "replaceExisting", defaultValue = "false") boolean replaceExisting
    ) {
        if (!hlsProperties.isEnabled()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error("HLS processing is disabled"));
        }

        if (!videoProcessingService.isFfmpegAvailable()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error("FFmpeg is not available on the server"));
        }

        HlsUploadService.UploadResponse response = hlsUploadService.initiateUpload(
                new HlsUploadService.UploadRequest(lessonId, videoFile, replaceExisting)
        );

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Get the processing status of an HLS video.
     *
     * GET /api/v1/hls/upload/{lessonId}/status
     */
    @GetMapping("/{lessonId}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'TMO', 'SME')")
    public ResponseEntity<ApiResponse<HlsUploadService.ProcessingStatus>> getStatus(
            @PathVariable UUID lessonId
    ) {
        HlsUploadService.ProcessingStatus status = hlsUploadService.getProcessingStatus(lessonId);
        return ResponseEntity.ok(ApiResponse.success(status));
    }

    /**
     * Delete HLS video for a lesson.
     *
     * DELETE /api/v1/hls/upload/{lessonId}
     */
    @DeleteMapping("/{lessonId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TMO', 'SME')")
    public ResponseEntity<ApiResponse<Void>> deleteVideo(@PathVariable UUID lessonId) {
        hlsUploadService.deleteHlsVideo(lessonId);
        return ResponseEntity.ok(ApiResponse.success("HLS video deleted successfully", null));
    }

    /**
     * Check if FFmpeg is available on the server.
     *
     * GET /api/v1/hls/upload/health
     */
    @GetMapping("/health")
    @PreAuthorize("hasAnyRole('ADMIN', 'TMO', 'SME')")
    public ResponseEntity<ApiResponse<HealthCheckResponse>> healthCheck() {
        boolean ffmpegAvailable = videoProcessingService.isFfmpegAvailable();
        boolean hlsEnabled = hlsProperties.isEnabled();

        String status;
        if (hlsEnabled && ffmpegAvailable) {
            status = "healthy";
        } else if (!hlsEnabled) {
            status = "disabled";
        } else {
            status = "ffmpeg_unavailable";
        }

        HealthCheckResponse response = new HealthCheckResponse(
                hlsEnabled,
                ffmpegAvailable,
                status
        );

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    public record HealthCheckResponse(
            boolean hlsEnabled,
            boolean ffmpegAvailable,
            String status
    ) {}
}
