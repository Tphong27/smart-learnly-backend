package com.smartlearnly.backend.flashcard.staging.service;

import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface FlashcardDocumentTextExtractionService {
    DocumentTextExtractionResult extract(MultipartFile file);

    record DocumentTextExtractionResult(
            String sourceType,
            String sourceName,
            String text,
            List<DocumentImage> images
    ) {
        public DocumentTextExtractionResult(String sourceType, String sourceName, String text) {
            this(sourceType, sourceName, text, List.of());
        }
    }

    record DocumentImage(
            String fileName,
            String contentType,
            byte[] content
    ) {
    }
}
