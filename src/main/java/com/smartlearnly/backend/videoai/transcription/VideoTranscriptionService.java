package com.smartlearnly.backend.videoai.transcription;

import java.nio.file.Path;
import java.util.List;

public interface VideoTranscriptionService {

    TranscriptionResult transcribe(Path audioFile, String sourceLanguage);

    record TranscriptionSegment(int index, long startMs, long endMs, String text) {
    }

    record TranscriptionResult(
            String language,
            double languageProbability,
            long durationMs,
            List<TranscriptionSegment> segments
    ) {
        public String fullText() {
            return segments.stream()
                    .map(TranscriptionSegment::text)
                    .reduce((left, right) -> left + " " + right)
                    .orElse("");
        }
    }
}
