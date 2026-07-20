package com.smartlearnly.backend.hls.dto;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
public record HlsProcessingCallbackRequest(
        @NotNull UUID jobId,
        @NotNull UUID lessonId,
        @NotNull @Pattern(regexp = "ready|failed") String status,
        @Size(max = 1024) String r2BasePath,
        @Size(max = 1100) String masterPlaylistPath,
        @Size(max = 8) List<@Pattern(regexp = "^[A-Za-z0-9]+$") String> qualities,
        @Size(max = 1024) String aiAudioObjectKey,
        @Positive Long aiAudioDurationMs,
        @Size(max = 1000) String errorMessage) {
    public boolean isReady() { return "ready".equals(status); }
}
