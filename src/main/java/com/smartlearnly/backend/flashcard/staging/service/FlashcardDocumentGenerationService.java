package com.smartlearnly.backend.flashcard.staging.service;

import com.smartlearnly.backend.flashcard.staging.service.FlashcardDocumentTextExtractionService.DocumentImage;
import com.smartlearnly.backend.flashcard.staging.service.FlashcardTextGenerationService.GenerationResult;
import java.util.List;

public interface FlashcardDocumentGenerationService {
    GenerationResult generate(DocumentGenerationRequest request);

    record DocumentGenerationRequest(
            String documentText,
            List<DocumentImage> images,
            List<DocumentImage> renderedPageImages,
            int desiredCount,
            String language,
            String difficulty,
            String sourceType,
            String sourceName
    ) {
        public DocumentGenerationRequest(
                String documentText,
                List<DocumentImage> images,
                int desiredCount,
                String language,
                String difficulty,
                String sourceType,
                String sourceName
        ) {
            this(documentText, images, List.of(), desiredCount, language, difficulty, sourceType, sourceName);
        }

        public DocumentGenerationRequest {
            images = images == null ? List.of() : List.copyOf(images);
            renderedPageImages = renderedPageImages == null ? List.of() : List.copyOf(renderedPageImages);
        }
    }
}
