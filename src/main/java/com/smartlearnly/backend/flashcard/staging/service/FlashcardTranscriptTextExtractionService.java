package com.smartlearnly.backend.flashcard.staging.service;

import org.springframework.web.multipart.MultipartFile;

public interface FlashcardTranscriptTextExtractionService {
    TranscriptTextExtractionResult extractRaw(String transcriptText, String sourceName);

    TranscriptTextExtractionResult extractFile(MultipartFile file);

    record TranscriptTextExtractionResult(
            String sourceName,
            String text
    ) {
    }
}
