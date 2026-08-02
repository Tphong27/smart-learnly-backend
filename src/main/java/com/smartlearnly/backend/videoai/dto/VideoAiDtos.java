package com.smartlearnly.backend.videoai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class VideoAiDtos {
        private VideoAiDtos() {
        }

        public record GenerateSummaryRequest(
                        @NotBlank(message = "YouTube URL is required") @Size(max = 500, message = "YouTube URL must not exceed 500 characters") String youtubeUrl) {
        }

        public record GenerateSummaryResponse(
                        String videoId,
                        String videoUrl,
                        long durationSeconds,
                        int durationMinutes,
                        GeneratedSummary summary) {
        }

        public record GeneratedSummary(
                        List<String> overviewParagraphs,
                        String keyTakeawaysTitle,
                        List<String> keyTakeaways) {
        }
}
