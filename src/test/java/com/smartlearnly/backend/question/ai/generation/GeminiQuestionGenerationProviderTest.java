package com.smartlearnly.backend.question.ai.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GeminiQuestionGenerationProviderTest {

    @Test
    void generate_sendsExpectedHeadersBodyAndParsesDirectOutputText() {
        ProviderFixture fixture = fixture("gemini-primary", "gemini-primary");
        String payload = questionPayload(UUID.randomUUID(), UUID.randomUUID());
        fixture.server.expect(requestTo("https://gemini.example.test/v1beta/interactions"))
                .andExpect(header("x-goog-api-key", "test-key"))
                .andExpect(header("Api-Revision", "2026-05-20"))
                .andExpect(jsonPath("$.model").value("gemini-primary"))
                .andExpect(jsonPath("$.response_format.mime_type").value("application/json"))
                .andExpect(jsonPath("$.input[0].type").value("text"))
                .andExpect(jsonPath("$.input[0].text").value(org.hamcrest.Matchers.containsString("Use only the provided SOURCE/CHUNK content")))
                .andExpect(jsonPath("$.input[0].text").value(org.hamcrest.Matchers.containsString("Lesson transcript")))
                .andRespond(withSuccess("{\"output_text\":" + quote(payload) + "}", MediaType.APPLICATION_JSON));

        QuestionGenerationProvider.GenerationResult result = fixture.provider.generate(requestWithSources());

        assertThat(result.questions()).hasSize(1);
        assertThat(result.questions().get(0).questionText()).isEqualTo("What is encapsulation?");
        assertThat(result.questions().get(0).answers()).hasSize(2);
        assertThat(result.promptTokens()).isNull();
        fixture.server.verify();
    }

    @Test
    void generate_usesDefaultModelAndNoSourcePromptWhenModelsAndSourcesAreBlank() {
        ProviderFixture fixture = fixture(" ", null);
        fixture.server.expect(requestTo("https://gemini.example.test/v1beta/interactions"))
                .andExpect(jsonPath("$.model").value("gemini-3.5-flash"))
                .andExpect(jsonPath("$.input[0].text").value(org.hamcrest.Matchers.containsString("No source material selected.")))
                .andExpect(jsonPath("$.input[0].text").value(org.hamcrest.Matchers.containsString("Generate clear, grounded draft questions")))
                .andRespond(withSuccess("{\"output_text\":\"{\\\"questions\\\":[]}\"}", MediaType.APPLICATION_JSON));

        QuestionGenerationProvider.GenerationResult result = fixture.provider.generate(requestWithoutSources(null, " "));

        assertThat(result.questions()).isEmpty();
        fixture.server.verify();
    }

    @Test
    void generate_stripsModelsPrefixAndAvoidsDuplicateFallbackModel() {
        ProviderFixture fixture = fixture("models/gemini-primary", "gemini-primary");
        fixture.server.expect(requestTo("https://gemini.example.test/v1beta/interactions"))
                .andExpect(jsonPath("$.model").value("gemini-primary"))
                .andRespond(withSuccess("{\"output_text\":\"{\\\"questions\\\":[]}\"}", MediaType.APPLICATION_JSON));

        QuestionGenerationProvider.GenerationResult result = fixture.provider.generate(requestWithSources());

        assertThat(result.questions()).isEmpty();
        fixture.server.verify();
    }

    @Test
    void generate_fallsBackWhenPrimaryModelTimesOutAtProvider() {
        ProviderFixture fixture = fixture("gemini-primary", "gemini-fallback");
        fixture.server.expect(requestTo("https://gemini.example.test/v1beta/interactions"))
                .andExpect(jsonPath("$.model").value("gemini-primary"))
                .andRespond(withStatus(HttpStatus.GATEWAY_TIMEOUT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"status\":\"DEADLINE_EXCEEDED\"}}"));
        fixture.server.expect(requestTo("https://gemini.example.test/v1beta/interactions"))
                .andExpect(jsonPath("$.model").value("gemini-fallback"))
                .andRespond(withSuccess("{\"output_text\":\"{\\\"questions\\\":[]}\"}", MediaType.APPLICATION_JSON));

        QuestionGenerationProvider.GenerationResult result = fixture.provider.generate(requestWithSources());

        assertThat(result.questions()).isEmpty();
        fixture.server.verify();
    }

    @Test
    void generate_fallsBackForAllRetryableProviderStatuses() {
        for (HttpStatus status : List.of(
                HttpStatus.BAD_REQUEST,
                HttpStatus.NOT_FOUND,
                HttpStatus.REQUEST_TIMEOUT,
                HttpStatus.TOO_MANY_REQUESTS,
                HttpStatus.INTERNAL_SERVER_ERROR
        )) {
            ProviderFixture fixture = fixture("gemini-primary", "gemini-fallback");
            fixture.server.expect(requestTo("https://gemini.example.test/v1beta/interactions"))
                    .andExpect(jsonPath("$.model").value("gemini-primary"))
                    .andRespond(withStatus(status)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body("{\"error\":{\"status\":\"retryable\"}}"));
            fixture.server.expect(requestTo("https://gemini.example.test/v1beta/interactions"))
                    .andExpect(jsonPath("$.model").value("gemini-fallback"))
                    .andRespond(withSuccess("{\"output_text\":\"{\\\"questions\\\":[]}\"}", MediaType.APPLICATION_JSON));

            QuestionGenerationProvider.GenerationResult result = fixture.provider.generate(requestWithSources());

            assertThat(result.questions()).isEmpty();
            fixture.server.verify();
        }
    }

    @Test
    void generate_returnsProviderHttpErrorWhenRetryableStatusHasNoFallbackCandidate() {
        ProviderFixture fixture = fixture("gemini-primary", "gemini-primary");
        fixture.server.expect(requestTo("https://gemini.example.test/v1beta/interactions"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"status\":\"INTERNAL\"}}"));

        assertThatThrownBy(() -> fixture.provider.generate(requestWithSources()))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.AI_PROVIDER_UNAVAILABLE);
                    assertThat(exception.getMessage()).contains("HTTP 500");
                });
        fixture.server.verify();
    }

    @Test
    void generate_fallsBackForTransportExceptionAndParsesNestedText() {
        ProviderFixture fixture = fixture("gemini-primary", "gemini-fallback");
        fixture.server.expect(requestTo("https://gemini.example.test/v1beta/interactions"))
                .andRespond(withException(new SocketTimeoutException("slow")));
        fixture.server.expect(requestTo("https://gemini.example.test/v1beta/interactions"))
                .andExpect(jsonPath("$.model").value("gemini-fallback"))
                .andRespond(withSuccess(
                        "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"```json\\n{\\\"questions\\\":[]}\\n```\"}]}}]}",
                        MediaType.APPLICATION_JSON));

        QuestionGenerationProvider.GenerationResult result = fixture.provider.generate(requestWithSources());

        assertThat(result.questions()).isEmpty();
        fixture.server.verify();
    }

    @Test
    void generate_parsesNestedTextWithoutClosingJsonFence() {
        ProviderFixture fixture = fixture("gemini-primary", null);
        fixture.server.expect(requestTo("https://gemini.example.test/v1beta/interactions"))
                .andRespond(withSuccess(
                        "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"```json\\n{\\\"questions\\\":[]}\"}]}}]}",
                        MediaType.APPLICATION_JSON));

        QuestionGenerationProvider.GenerationResult result = fixture.provider.generate(requestWithSources());

        assertThat(result.questions()).isEmpty();
        fixture.server.verify();
    }

    @Test
    void generate_doesNotFallbackForUnauthorizedHttpResponse() {
        ProviderFixture fixture = fixture("gemini-primary", "gemini-fallback");
        fixture.server.expect(requestTo("https://gemini.example.test/v1beta/interactions"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.TEXT_PLAIN)
                        .body("bad key"));

        assertThatThrownBy(() -> fixture.provider.generate(requestWithSources()))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.AI_PROVIDER_UNAVAILABLE);
                    assertThat(exception.getMessage()).contains("HTTP 401");
                });
        fixture.server.verify();
    }

    @Test
    void generate_redactsBlankEndpointAndTruncatesProviderErrorBodyInHttpFailures() {
        ProviderFixture blankEndpoint = fixture("gemini-primary", "gemini-fallback");
        blankEndpoint.properties.setApiBaseUrl(null);
        blankEndpoint.server.expect(requestTo("https://gemini.example.test/v1beta/interactions"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> blankEndpoint.provider.generate(requestWithSources()))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.AI_PROVIDER_UNAVAILABLE);
                    assertThat(exception.getMessage()).contains("HTTP 403");
                });
        blankEndpoint.server.verify();

        ProviderFixture longBody = fixture("gemini-primary", "gemini-fallback");
        longBody.properties.setApiBaseUrl("https://gemini.example.test/v1beta?key=secret");
        longBody.server.expect(requestTo("https://gemini.example.test/v1beta/interactions"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.TEXT_PLAIN)
                        .body("x".repeat(1700)));

        assertThatThrownBy(() -> longBody.provider.generate(requestWithSources()))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.AI_PROVIDER_UNAVAILABLE);
                    assertThat(exception.getMessage()).contains("HTTP 403");
                });
        longBody.server.verify();
    }

    @Test
    void generate_throwsUnavailableWhenFinalTransportAttemptFails() {
        ProviderFixture fixture = fixture("gemini-primary", "gemini-primary");
        fixture.server.expect(requestTo("https://gemini.example.test/v1beta/interactions"))
                .andRespond(withException(new IOException("network down")));

        assertThatThrownBy(() -> fixture.provider.generate(requestWithSources()))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.AI_PROVIDER_UNAVAILABLE);
                    assertThat(exception.getMessage()).isEqualTo("AI provider request failed");
                });
        fixture.server.verify();
    }

    @Test
    void generate_throwsOutputInvalidForMissingBlankNullMalformedOrUnresolvableResponses() {
        ProviderFixture missingText = fixture("gemini-primary", null);
        missingText.server.expect(requestTo("https://gemini.example.test/v1beta/interactions"))
                .andRespond(withSuccess("{\"candidates\":[]}", MediaType.APPLICATION_JSON));
        assertOutputInvalid(missingText);

        ProviderFixture blankText = fixture("gemini-primary", null);
        blankText.server.expect(requestTo("https://gemini.example.test/v1beta/interactions"))
                .andRespond(withSuccess("{\"output_text\":\" \"}", MediaType.APPLICATION_JSON));
        assertOutputInvalid(blankText);

        ProviderFixture nullText = fixture("gemini-primary", null);
        nullText.server.expect(requestTo("https://gemini.example.test/v1beta/interactions"))
                .andRespond(withSuccess("{\"output_text\":null}", MediaType.APPLICATION_JSON));
        assertOutputInvalid(nullText);

        ProviderFixture unresolvableTree = fixture("gemini-primary", null);
        unresolvableTree.server.expect(requestTo("https://gemini.example.test/v1beta/interactions"))
                .andRespond(withSuccess(
                        "{\"wrapper\":{\"text\":null},\"candidates\":[{\"content\":{\"parts\":[{\"notText\":\"x\"}]}}]}",
                        MediaType.APPLICATION_JSON));
        assertOutputInvalid(unresolvableTree);

        ProviderFixture malformedJson = fixture("gemini-primary", null);
        malformedJson.server.expect(requestTo("https://gemini.example.test/v1beta/interactions"))
                .andRespond(withSuccess("{\"outputText\":\"not-json\"}", MediaType.APPLICATION_JSON));
        assertOutputInvalid(malformedJson);

        ProviderFixture nullBody = fixture("gemini-primary", null);
        nullBody.server.expect(requestTo("https://gemini.example.test/v1beta/interactions"))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));
        assertOutputInvalid(nullBody);
    }

    @Test
    void generate_returnsEmptyQuestionsWhenPayloadQuestionListIsNull() {
        ProviderFixture fixture = fixture("gemini-primary", null);
        fixture.server.expect(requestTo("https://gemini.example.test/v1beta/interactions"))
                .andRespond(withSuccess("{\"output_text\":\"{}\"}", MediaType.APPLICATION_JSON));

        QuestionGenerationProvider.GenerationResult result = fixture.provider.generate(requestWithSources());

        assertThat(result.questions()).isEmpty();
        fixture.server.verify();
    }

    @Test
    void generate_rejectsUnavailableConfigurationsBeforeCallingProvider() {
        assertUnavailable(properties -> properties.setEnabled(false), "disabled");
        assertUnavailable(properties -> properties.setProvider("openai"), "provider is not configured");
        assertUnavailable(properties -> properties.setApiKey(null), "API key is not configured");
        assertUnavailable(properties -> properties.setApiKey(" "), "API key is not configured");
    }

    @Test
    void metadataMethods_returnProviderModelAndPromptVersion() {
        ProviderFixture fixture = fixture("gemini-primary", null);

        assertThat(fixture.provider.providerName()).isEqualTo("gemini");
        assertThat(fixture.provider.modelName()).isEqualTo("gemini-primary");
        assertThat(fixture.provider.promptVersion()).isEqualTo("question-ai-generation-v1");
    }

    private void assertOutputInvalid(ProviderFixture fixture) {
        assertThatThrownBy(() -> fixture.provider.generate(requestWithSources()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.AI_PROVIDER_OUTPUT_INVALID));
        fixture.server.verify();
    }

    private void assertUnavailable(
            java.util.function.Consumer<QuestionAiGenerationProperties> customizer,
            String message
    ) {
        ProviderFixture fixture = fixture("gemini-primary", null);
        customizer.accept(fixture.properties);

        assertThatThrownBy(() -> fixture.provider.generate(requestWithSources()))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.AI_PROVIDER_UNAVAILABLE);
                    assertThat(exception.getMessage()).contains(message);
                });
    }

    private ProviderFixture fixture(String model, String fallbackModel) {
        QuestionAiGenerationProperties properties = new QuestionAiGenerationProperties();
        properties.setEnabled(true);
        properties.setProvider("gemini");
        properties.setApiKey("test-key");
        properties.setApiBaseUrl("https://gemini.example.test/v1beta");
        properties.setModel(model);
        properties.setFallbackModel(fallbackModel);
        properties.setTimeout(Duration.ofSeconds(2));

        RestClient.Builder builder = RestClient.builder().baseUrl(properties.getApiBaseUrl());
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GeminiQuestionGenerationProvider provider =
                new GeminiQuestionGenerationProvider(properties, builder.build());
        return new ProviderFixture(properties, provider, server);
    }

    private QuestionGenerationProvider.GenerationRequest requestWithSources() {
        UUID sourceId = UUID.randomUUID();
        UUID chunkId = UUID.randomUUID();
        return new QuestionGenerationProvider.GenerationRequest(
                UUID.randomUUID(),
                3,
                List.of("multiple_choice"),
                "en",
                "Generate grounded questions.",
                List.of(new QuestionGenerationProvider.SourceInput(
                        sourceId,
                        "Lesson transcript",
                        "checksum",
                        "1",
                        List.of(new QuestionGenerationProvider.ChunkInput(
                                chunkId,
                                "00:00-00:10",
                                "A short grounded source excerpt.")))));
    }

    private QuestionGenerationProvider.GenerationRequest requestWithoutSources(
            List<QuestionGenerationProvider.SourceInput> sources,
            String instruction
    ) {
        return new QuestionGenerationProvider.GenerationRequest(
                UUID.randomUUID(),
                2,
                List.of("true_false"),
                "vi",
                instruction,
                sources);
    }

    private String questionPayload(UUID sourceId, UUID chunkId) {
        return """
                {
                  "questions": [
                    {
                      "questionText": "What is encapsulation?",
                      "questionType": "multiple_choice",
                      "answers": [
                        {"answerText": "Keeping state and behavior together", "correct": true, "orderIndex": 1},
                        {"answerText": "Deleting all methods", "correct": false, "orderIndex": 2}
                      ],
                      "explanation": "Encapsulation groups related data and behavior.",
                      "evidence": [
                        {
                          "generationSourceId": "%s",
                          "chunkId": "%s",
                          "chunkReference": "00:00-00:10",
                          "excerpt": "A short grounded source excerpt.",
                          "supportsCorrectAnswer": true
                        }
                      ]
                    }
                  ]
                }
                """.formatted(sourceId, chunkId);
    }

    private String quote(String value) {
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r") + "\"";
    }

    private record ProviderFixture(
            QuestionAiGenerationProperties properties,
            GeminiQuestionGenerationProvider provider,
            MockRestServiceServer server
    ) {
    }
}
