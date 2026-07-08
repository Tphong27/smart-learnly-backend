package com.smartlearnly.backend.flashcard.staging.service;

import com.smartlearnly.backend.flashcard.staging.service.FlashcardDocumentTextExtractionService.DocumentImage;
import com.smartlearnly.backend.flashcard.staging.service.FlashcardTextGenerationService.GenerationResult;
import java.util.List;

public interface FlashcardDocumentGenerationService {
    GenerationResult generate(DocumentGenerationRequest request);

    record DocumentGenerationRequest(
            String documentText,
            List<DocumentImage> images,
            int desiredCount,
            String language,
            String difficulty,
            String sourceType,
            String sourceName
    ) {
    }
}
