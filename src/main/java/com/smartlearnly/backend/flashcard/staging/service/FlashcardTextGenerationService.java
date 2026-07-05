package com.smartlearnly.backend.flashcard.staging.service;

import java.util.List;

public interface FlashcardTextGenerationService {
    GenerationResult generate(GenerationRequest request);

    record GenerationRequest(
            String sourceText,
            int desiredCount,
            String language,
            String difficulty,
            String generationMode
    ) {
    }

    record GenerationResult(
            String sourceType,
            List<GeneratedFlashcardCandidate> candidates
    ) {
    }

    record GeneratedFlashcardCandidate(
            String frontText,
            String backText,
            String explanation,
            String sourceExcerpt
    ) {
    }
}
