package com.smartlearnly.backend.flashcard.staging.service;

import com.smartlearnly.backend.flashcard.staging.service.FlashcardDocumentGenerationService.DocumentGenerationRequest;
import com.smartlearnly.backend.flashcard.staging.service.FlashcardGeminiGenerationService.GeminiGenerationRequest;
import com.smartlearnly.backend.flashcard.staging.service.FlashcardTextGenerationService.GenerationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GeminiFlashcardDocumentGenerationService implements FlashcardDocumentGenerationService {
    private final FlashcardGeminiGenerationService geminiGenerationService;

    @Override
    public GenerationResult generate(DocumentGenerationRequest request) {
        return geminiGenerationService.generate(GeminiGenerationRequest.document(
                request == null ? null : request.documentText(),
                request == null ? null : request.images(),
                request == null ? null : request.renderedPageImages(),
                request == null ? 0 : request.desiredCount(),
                request == null ? null : request.language(),
                request == null ? null : request.sourceType(),
                request == null ? null : request.sourceName()
        ));
    }
}
