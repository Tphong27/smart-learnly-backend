package com.smartlearnly.backend.videoai.generation;

import com.smartlearnly.backend.videoai.transcription.VideoTranscriptionService.TranscriptionSegment;
import java.util.List;

public interface VideoLearningAidGenerationService {
    LearningAidResult generate(String language, List<TranscriptionSegment> segments);

    record GeneratedChapter(int startSegmentIndex, int endSegmentIndex, String title, String summary) {
    }

    record LearningAidResult(
            String suggestedTitle,
            String summary,
            List<String> keyPoints,
            List<GeneratedChapter> chapters
    ) {
    }
}
