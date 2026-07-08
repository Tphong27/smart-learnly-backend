package com.smartlearnly.backend.flashcard.staging.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.flashcard.staging.service.FlashcardDocumentGenerationService.DocumentGenerationRequest;
import com.smartlearnly.backend.flashcard.staging.service.FlashcardTextGenerationService.GenerationResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class GeminiFlashcardDocumentGenerationServiceTest {
    private final GeminiFlashcardDocumentGenerationService service =
            new GeminiFlashcardDocumentGenerationService(properties());

    @Test
    void parseGenerationOutputAcceptsStrictJsonAndOptionalFields() {
        String json = """
                {
                  "cards": [
                    {
                      "frontText": "What is CRUD?",
                      "backText": "Create, Read, Update, and Delete.",
                      "hint": "Four basic operations",
                      "explanation": "CRUD describes common data operations.",
                      "sourceExcerpt": "CRUD operations"
                    }
                  ]
                }
                """;

        GenerationResult result = service.parseGenerationOutput(json, 5);

        assertThat(result.candidates()).hasSize(1);
        assertThat(result.candidates().get(0).frontText()).isEqualTo("What is CRUD?");
        assertThat(result.candidates().get(0).backText()).isEqualTo("Create, Read, Update, and Delete.");
        assertThat(result.candidates().get(0).hint()).isEqualTo("Four basic operations");
        assertThat(result.candidates().get(0).explanation()).isEqualTo("CRUD describes common data operations.");
        assertThat(result.candidates().get(0).sourceExcerpt()).isEqualTo("CRUD operations");
    }

    @Test
    void parseGenerationOutputStripsJsonFenceOnlyAsFallback() {
        String fenced = """
                ```json
                {"cards":[{"frontText":"API method?","backText":"POST","hint":null,"explanation":null,"sourceExcerpt":null}]}
                ```
                """;

        GenerationResult result = service.parseGenerationOutput(fenced, 1);

        assertThat(result.candidates()).hasSize(1);
        assertThat(result.candidates().get(0).frontText()).isEqualTo("API method?");
    }

    @Test
    void parseGenerationOutputDeduplicatesAndTrimsToTarget() {
        String json = """
                {
                  "cards": [
                    {"frontText":"What is HTTP?","backText":"A protocol.","hint":null,"explanation":null,"sourceExcerpt":null},
                    {"frontText":" what  is http? ","backText":" a protocol. ","hint":null,"explanation":null,"sourceExcerpt":null},
                    {"frontText":"What is REST?","backText":"An API architectural style.","hint":null,"explanation":null,"sourceExcerpt":null},
                    {"frontText":"What is JSON?","backText":"A data format.","hint":null,"explanation":null,"sourceExcerpt":null}
                  ]
                }
                """;

        GenerationResult result = service.parseGenerationOutput(json, 2);

        assertThat(result.candidates()).hasSize(2);
        assertThat(result.candidates()).extracting(candidate -> candidate.frontText())
                .containsExactly("What is HTTP?", "What is REST?");
    }

    @Test
    void parseGenerationOutputRejectsInvalidOrEmptyCards() {
        assertThatThrownBy(() -> service.parseGenerationOutput("not json", 10))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));

        assertThatThrownBy(() -> service.parseGenerationOutput("{\"cards\":[]}", 10))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));

        assertThatThrownBy(() -> service.parseGenerationOutput(
                "{\"cards\":[{\"frontText\":\" \",\"backText\":\"Answer\"}]}",
                10
        ))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
    }

    @Test
    void parseGenerationOutputSkipsOverlongRequiredFields() {
        String json = """
                {
                  "cards": [
                    {"frontText":"%s","backText":"Answer","hint":null,"explanation":null,"sourceExcerpt":null}
                  ]
                }
                """.formatted("x".repeat(2001));

        assertThatThrownBy(() -> service.parseGenerationOutput(json, 1))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
    }

    @Test
    void generationPromptCapturesLanguageBehavior() {
        String autoPrompt = service.buildGenerationPrompt("auto", "medium", 10, "PDF", "lesson.pdf");
        String viPrompt = service.buildGenerationPrompt("vi", "hard", 8, "DOCX", "lesson.docx");
        String enPrompt = service.buildGenerationPrompt("en", "easy", 6, "DOCX", "lesson.docx");

        assertThat(autoPrompt).contains("Target language: Auto-detect");
        assertThat(autoPrompt).contains("For Auto-detect, follow the main document language");
        assertThat(viPrompt).contains("Target language: Vietnamese");
        assertThat(viPrompt).contains("natural Vietnamese");
        assertThat(enPrompt).contains("Target language: English");
        assertThat(enPrompt).contains("natural English");
        assertThat(viPrompt).contains("Do not mix Vietnamese and English");
        assertThat(enPrompt).contains("Do not mix Vietnamese and English");
    }

    @Test
    void generateRejectsPdfWhenMergedContentIsStillUnusable() {
        DocumentGenerationRequest request = new DocumentGenerationRequest(
                "Too short.",
                List.of(),
                10,
                "auto",
                "medium",
                "PDF",
                "scan.pdf"
        );

        assertThatThrownBy(() -> service.generate(request))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST);
                    assertThat(exception.getMessage()).contains("Scanned PDFs are not supported yet");
                });
    }

    private static FlashcardDocumentGenerationProperties properties() {
        FlashcardDocumentGenerationProperties properties = new FlashcardDocumentGenerationProperties();
        properties.setApiKey("test-key");
        return properties;
    }
}
