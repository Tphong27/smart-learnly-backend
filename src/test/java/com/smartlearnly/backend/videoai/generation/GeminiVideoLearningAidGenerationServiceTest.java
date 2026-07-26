package com.smartlearnly.backend.videoai.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlearnly.backend.videoai.transcription.VideoTranscriptionService.TranscriptionSegment;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GeminiVideoLearningAidGenerationServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void generatesStructuredLearningAidsThroughGenerateContent() throws Exception {
        VideoAiGenerationProperties properties = properties("gemini-3.1-flash-lite", "gemini-2.5-flash");
        RestClient.Builder builder = RestClient.builder().baseUrl(properties.getApiBaseUrl());
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GeminiVideoLearningAidGenerationService service = new GeminiVideoLearningAidGenerationService(
                properties, objectMapper, builder.build());

        server.expect(requestTo("https://gemini.example.test/v1beta/models/"
                        + "gemini-3.1-flash-lite:generateContent"))
                .andExpect(header("x-goog-api-key", "test-key"))
                .andExpect(jsonPath("$.generationConfig.responseMimeType").value("application/json"))
                .andExpect(jsonPath("$.generationConfig.responseJsonSchema.type").value("object"))
                .andRespond(withSuccess(response("Generated title"), MediaType.APPLICATION_JSON));

        var result = service.generate("en", segments());

        assertThat(result.suggestedTitle()).isEqualTo("Generated title");
        assertThat(result.summary()).isEqualTo("A grounded summary.");
        assertThat(result.keyPoints()).containsExactly("First point");
        assertThat(result.chapters()).hasSize(1);
        server.verify();
    }

    @Test
    void fallsBackImmediatelyWhenConfiguredModelReturnsServerError() throws Exception {
        VideoAiGenerationProperties properties = properties("gemini-3.5-flash", "gemini-3.1-flash-lite");
        RestClient.Builder builder = RestClient.builder().baseUrl(properties.getApiBaseUrl());
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GeminiVideoLearningAidGenerationService service = new GeminiVideoLearningAidGenerationService(
                properties, objectMapper, builder.build());

        server.expect(requestTo("https://gemini.example.test/v1beta/models/gemini-3.5-flash:generateContent"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"status\":\"INTERNAL\"}}"));
        server.expect(requestTo("https://gemini.example.test/v1beta/models/"
                        + "gemini-3.1-flash-lite:generateContent"))
                .andRespond(withSuccess(response("Fallback title"), MediaType.APPLICATION_JSON));

        var result = service.generate("en", segments());

        assertThat(result.suggestedTitle()).isEqualTo("Fallback title");
        server.verify();
    }

    private VideoAiGenerationProperties properties(String model, String fallbackModel) {
        VideoAiGenerationProperties properties = new VideoAiGenerationProperties();
        properties.setEnabled(true);
        properties.setApiKey("test-key");
        properties.setApiBaseUrl("https://gemini.example.test/v1beta");
        properties.setModel(model);
        properties.setFallbackModel(fallbackModel);
        return properties;
    }

    private List<TranscriptionSegment> segments() {
        return List.of(
                new TranscriptionSegment(0, 0, 1_000, "First transcript segment."),
                new TranscriptionSegment(1, 1_000, 2_000, "Second transcript segment."));
    }

    private String response(String title) throws Exception {
        String generated = objectMapper.writeValueAsString(Map.of(
                "suggestedTitle", title,
                "summary", "A grounded summary.",
                "keyPoints", List.of("First point"),
                "chapters", List.of(Map.of(
                        "startSegmentIndex", 0,
                        "endSegmentIndex", 1,
                        "title", "Overview",
                        "summary", "Chapter summary."))));
        return objectMapper.writeValueAsString(Map.of(
                "candidates", List.of(Map.of(
                        "content", Map.of(
                                "parts", List.of(Map.of("text", generated)))))));
    }
}
