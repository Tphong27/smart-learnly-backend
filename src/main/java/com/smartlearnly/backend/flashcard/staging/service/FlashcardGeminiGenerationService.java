package com.smartlearnly.backend.flashcard.staging.service;

import com.smartlearnly.backend.flashcard.staging.service.FlashcardDocumentTextExtractionService.DocumentImage;
import com.smartlearnly.backend.flashcard.staging.service.FlashcardTextGenerationService.GenerationResult;
import java.util.List;

public interface FlashcardGeminiGenerationService {
    GenerationResult generate(GeminiGenerationRequest request);

    record GeminiGenerationRequest(
            String sourceText,
            List<DocumentImage> images,
            List<DocumentImage> renderedPageImages,
            int desiredCount,
            String language,
            String difficulty,
            String sourceType,
            String sourceName,
            String sourceContentLabel
    ) {
        public GeminiGenerationRequest {
            images = images == null ? List.of() : List.copyOf(images);
            renderedPageImages = renderedPageImages == null ? List.of() : List.copyOf(renderedPageImages);
            sourceContentLabel = sourceContentLabel == null || sourceContentLabel.isBlank()
                    ? "Document content"
                    : sourceContentLabel.trim();
        }

        public static GeminiGenerationRequest document(
                String documentText,
                List<DocumentImage> images,
                List<DocumentImage> renderedPageImages,
                int desiredCount,
                String language,
                String difficulty,
                String sourceType,
                String sourceName
        ) {
            return new GeminiGenerationRequest(
                    documentText,
                    images,
                    renderedPageImages,
                    desiredCount,
                    language,
                    difficulty,
                    sourceType,
                    sourceName,
                    "Document content"
            );
        }

        public static GeminiGenerationRequest text(
                String sourceText,
                int desiredCount,
                String language,
                String difficulty,
                String sourceType,
                String sourceName
        ) {
            return new GeminiGenerationRequest(
                    sourceText,
                    List.of(),
                    List.of(),
                    desiredCount,
                    language,
                    difficulty,
                    sourceType,
                    sourceName,
                    "Source text"
            );
        }
    }
}
