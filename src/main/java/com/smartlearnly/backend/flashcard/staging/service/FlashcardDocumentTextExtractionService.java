package com.smartlearnly.backend.flashcard.staging.service;

import org.springframework.web.multipart.MultipartFile;

public interface FlashcardDocumentTextExtractionService {
    DocumentTextExtractionResult extract(MultipartFile file);

    record DocumentTextExtractionResult(
            String sourceType,
            String sourceName,
            String text
    ) {
    }
}
