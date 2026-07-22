package com.smartlearnly.backend.flashcard.staging.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.flashcard.staging.service.FlashcardDocumentGenerationService.DocumentGenerationRequest;
import com.smartlearnly.backend.flashcard.staging.service.FlashcardTextGenerationService.GenerationResult;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

class GeminiFlashcardDocumentGenerationServiceTest {
    private final GeminiFlashcardDocumentGenerationService service =
            new GeminiFlashcardDocumentGenerationService(properties());

    @Test
    void fallsBackWhenPrimaryDocumentModelIsUnavailable() {
        FlashcardDocumentGenerationProperties properties = properties();
        properties.setApiBaseUrl("https://gemini.example.test/v1beta");
        properties.setModel("gemini-primary");
        properties.setFallbackModel("gemini-fallback");
        RestClient.Builder builder = RestClient.builder().baseUrl(properties.getApiBaseUrl());
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GeminiFlashcardDocumentGenerationService fallbackService =
                new GeminiFlashcardDocumentGenerationService(properties, builder.build());

        server.expect(requestTo("https://gemini.example.test/v1beta/interactions"))
                .andExpect(jsonPath("$.model").value("gemini-primary"))
                .andRespond(withStatus(HttpStatus.GATEWAY_TIMEOUT));
        server.expect(requestTo("https://gemini.example.test/v1beta/interactions"))
                .andExpect(jsonPath("$.model").value("gemini-fallback"))
                .andRespond(withSuccess("{\"output_text\":\"fallback output\"}", MediaType.APPLICATION_JSON));

        String output = fallbackService.sendGeminiInput(
                List.of(Map.of("type", "text", "text", "Read this document.")),
                "test");

        assertThat(output).isEqualTo("fallback output");
        server.verify();
    }

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
                {"cards":[{"frontText":"API method?","backText":"POST","hint":null,"explanation":null,"sourceExcerpt":"The document uses POST."}]}
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
                    {"frontText":"What is HTTP?","backText":"A protocol.","hint":null,"explanation":null,"sourceExcerpt":"HTTP is a protocol."},
                    {"frontText":" what  is http? ","backText":" a protocol. ","hint":null,"explanation":null,"sourceExcerpt":"HTTP is a protocol."},
                    {"frontText":"What is REST?","backText":"An API architectural style.","hint":null,"explanation":null,"sourceExcerpt":"REST is an API architectural style."},
                    {"frontText":"What is JSON?","backText":"A data format.","hint":null,"explanation":null,"sourceExcerpt":"JSON is a data format."}
                  ]
                }
                """;

        GenerationResult result = service.parseGenerationOutput(json, 2);

        assertThat(result.candidates()).hasSize(2);
        assertThat(result.candidates()).extracting(candidate -> candidate.frontText())
                .containsExactly("What is HTTP?", "What is REST?");
    }

    @Test
    void parseGenerationOutputAllowsShortfallWhenDocumentOnlySupportsFewerCards() {
        String json = """
                {
                  "cards": [
                    {
                      "frontText": "What does staging protect?",
                      "backText": "It keeps draft flashcards separate until approval.",
                      "hint": null,
                      "explanation": null,
                      "sourceExcerpt": "Draft cards stay separate until an admin approves them."
                    }
                  ]
                }
                """;

        GenerationResult result = service.parseGenerationOutput(json, 10);

        assertThat(result.candidates()).hasSize(1);
    }

    @Test
    void parseGenerationOutputRejectsCardsWithoutClearSourceSupport() {
        String json = """
                {
                  "cards": [
                    {"frontText":"What is Spring Boot?","backText":"A Java framework.","hint":null,"explanation":null,"sourceExcerpt":null},
                    {"frontText":"What is Docker?","backText":"A container platform.","hint":null,"explanation":null}
                  ]
                }
                """;

        assertThatThrownBy(() -> service.parseGenerationOutput(json, 10))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST);
                    assertThat(exception.getMessage()).contains("No usable flashcards");
                });
    }

    @Test
    void parseGenerationOutputClearsEnglishSourceExcerptForVietnameseTarget() {
        String json = """
                {
                  "cards": [
                    {
                      "frontText": "Dịch vụ staging bảo vệ điều gì?",
                      "backText": "Dịch vụ giữ thẻ nháp tách biệt cho đến khi được duyệt.",
                      "hint": null,
                      "explanation": null,
                      "sourceExcerpt": "The staging service keeps draft cards separate from current flashcards."
                    }
                  ]
                }
                """;

        GenerationResult result = service.parseGenerationOutput(json, 10, "vi");

        assertThat(result.candidates()).hasSize(1);
        assertThat(result.candidates().get(0).sourceExcerpt()).isNull();
    }

    @Test
    void parseGenerationOutputClearsVietnameseSourceExcerptForEnglishTarget() {
        String json = """
                {
                  "cards": [
                    {
                      "frontText": "What does staging protect?",
                      "backText": "It keeps draft cards separate until approval.",
                      "hint": null,
                      "explanation": null,
                      "sourceExcerpt": "Thẻ nháp được giữ riêng cho đến khi quản trị viên duyệt."
                    }
                  ]
                }
                """;

        GenerationResult result = service.parseGenerationOutput(json, 10, "en");

        assertThat(result.candidates()).hasSize(1);
        assertThat(result.candidates().get(0).sourceExcerpt()).isNull();
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
                    {"frontText":"%s","backText":"Answer","hint":null,"explanation":null,"sourceExcerpt":"Supported answer"}
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
        assertThat(viPrompt).contains("sourceExcerpt must also be Vietnamese");
        assertThat(enPrompt).contains("Target language: English");
        assertThat(enPrompt).contains("natural English");
        assertThat(enPrompt).contains("sourceExcerpt must also be English");
        assertThat(autoPrompt).contains("Maximum target cards: 10");
        assertThat(autoPrompt).contains("fewer cards are correct");
        assertThat(autoPrompt).contains("Every card must be grounded");
        assertThat(autoPrompt).contains("Do not invent extra cards");
        assertThat(autoPrompt).contains("Do not use outside knowledge, including Spring Boot knowledge");
        assertThat(viPrompt).contains("set sourceExcerpt to null rather than mixing languages");
        assertThat(viPrompt).contains("Do not mix Vietnamese and English");
        assertThat(enPrompt).contains("Do not mix Vietnamese and English");
    }

    @Test
    void providerLimitHttpErrorsUseFriendlyDocumentMessage() {
        RestClientResponseException exception = new RestClientResponseException(
                "Too Many Requests",
                429,
                "Too Many Requests",
                null,
                "{\"error\":\"too_many_requests\",\"message\":\"quota exceeded\"}".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
        );

        BusinessException mapped = service.toProviderHttpException(exception);

        assertThat(mapped.errorCode()).isEqualTo(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
        assertThat(mapped.getMessage())
                .isEqualTo("Document image reading is temporarily unavailable. Please try again later or use a text-based document.");
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
                    assertThat(exception.getMessage()).contains("Scanned PDF pages could not be read");
                });
    }

    private static FlashcardDocumentGenerationProperties properties() {
        FlashcardDocumentGenerationProperties properties = new FlashcardDocumentGenerationProperties();
        properties.setApiKey("test-key");
        return properties;
    }
}
