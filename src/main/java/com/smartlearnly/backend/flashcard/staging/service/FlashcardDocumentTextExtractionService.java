package com.smartlearnly.backend.flashcard.staging.service;

import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface FlashcardDocumentTextExtractionService {
    DocumentTextExtractionResult extract(MultipartFile file);

    record DocumentTextExtractionResult(
            String sourceType,
            String sourceName,
            String text,
            List<DocumentImage> images,
            List<DocumentImage> renderedPageImages
    ) {
        public DocumentTextExtractionResult(String sourceType, String sourceName, String text) {
            this(sourceType, sourceName, text, List.of(), List.of());
        }

        public DocumentTextExtractionResult(String sourceType, String sourceName, String text, List<DocumentImage> images) {
            this(sourceType, sourceName, text, images, List.of());
        }

        public DocumentTextExtractionResult {
            images = images == null ? List.of() : List.copyOf(images);
            renderedPageImages = renderedPageImages == null ? List.of() : List.copyOf(renderedPageImages);
        }
    }

    record DocumentImage(
            String fileName,
            String contentType,
            byte[] content
    ) {
    }
}
