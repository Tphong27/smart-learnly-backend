package com.smartlearnly.backend.question.ai.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GeminiQuestionGenerationProviderTest {

    @Test
    void fallsBackWhenPrimaryModelTimesOutAtProvider() {
        QuestionAiGenerationProperties properties = new QuestionAiGenerationProperties();
        properties.setEnabled(true);
        properties.setApiKey("test-key");
        properties.setApiBaseUrl("https://gemini.example.test/v1beta");
        properties.setModel("gemini-primary");
        properties.setFallbackModel("gemini-fallback");

        RestClient.Builder builder = RestClient.builder().baseUrl(properties.getApiBaseUrl());
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GeminiQuestionGenerationProvider provider =
                new GeminiQuestionGenerationProvider(properties, builder.build());

        server.expect(requestTo("https://gemini.example.test/v1beta/interactions"))
                .andExpect(header("x-goog-api-key", "test-key"))
                .andExpect(jsonPath("$.model").value("gemini-primary"))
                .andRespond(withStatus(HttpStatus.GATEWAY_TIMEOUT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"status\":\"DEADLINE_EXCEEDED\"}}"));
        server.expect(requestTo("https://gemini.example.test/v1beta/interactions"))
                .andExpect(jsonPath("$.model").value("gemini-fallback"))
                .andRespond(withSuccess(
                        "{\"output_text\":\"{\\\"questions\\\":[]}\"}",
                        MediaType.APPLICATION_JSON));

        QuestionGenerationProvider.GenerationResult result = provider.generate(request());

        assertThat(result.questions()).isEmpty();
        server.verify();
    }

    private QuestionGenerationProvider.GenerationRequest request() {
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
}
