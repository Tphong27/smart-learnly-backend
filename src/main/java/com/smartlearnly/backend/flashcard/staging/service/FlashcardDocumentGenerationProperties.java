package com.smartlearnly.backend.flashcard.staging.service;

import java.time.Duration;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.flashcard-document-generation")
public class FlashcardDocumentGenerationProperties {
    private boolean enabled = true;
    private String provider = "gemini";
    private String apiKey;
    private String apiBaseUrl = "https://generativelanguage.googleapis.com/v1beta";
    private String model = "gemini-3.5-flash";
    private Duration timeout = Duration.ofSeconds(60);
    private int maxEmbeddedImages = 2;
    private DataSize maxEmbeddedImageSize = DataSize.ofMegabytes(10);
    private int maxRenderedPdfPages = 3;
    private float pdfRenderDpi = 120F;
    private DataSize maxRenderedPageImageSize = DataSize.ofMegabytes(5);
    private float renderedPageJpegQuality = 0.78F;
    private List<String> allowedImageContentTypes = List.of("image/png", "image/jpeg", "image/webp");
}
