package com.smartlearnly.backend.question.ai.generation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.question.ai.dto.AiQuestionDraftDtos;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Component
public class GeminiQuestionGenerationProvider implements QuestionGenerationProvider {
    private static final String PROVIDER_NAME = "gemini";
    private static final String PROMPT_VERSION = "question-ai-generation-v1";

    private final QuestionAiGenerationProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestClient restClient;

    @Autowired
    public GeminiQuestionGenerationProvider(QuestionAiGenerationProperties properties) {
        this(properties, createRestClient(properties));
    }

    GeminiQuestionGenerationProvider(
            QuestionAiGenerationProperties properties,
            RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    @Override
    public GenerationResult generate(GenerationRequest request) {
        ensureAvailable();
        try {
            String response = sendWithFallback(request);
            return parseResponse(response);
        } catch (BusinessException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            log.warn(
                    "Gemini question generation HTTP error: status={} model={} endpoint={} responseBody={}",
                    exception.getStatusCode().value(),
                    properties.getModel(),
                    sanitizeEndpoint(properties.getApiBaseUrl()),
                    truncateForLog(exception.getResponseBodyAsString(), 1600),
                    exception
            );
            throw new BusinessException(ErrorCode.AI_PROVIDER_UNAVAILABLE, "AI provider returned HTTP " + exception.getStatusCode().value());
        } catch (IOException | IllegalArgumentException exception) {
            log.warn(
                    "Gemini question generation response parse error: model={} endpoint={} reason={}",
                    properties.getModel(),
                    sanitizeEndpoint(properties.getApiBaseUrl()),
                    exception.getMessage(),
                    exception
            );
            throw new BusinessException(ErrorCode.AI_PROVIDER_OUTPUT_INVALID, "AI provider returned an invalid response");
        } catch (RestClientException exception) {
            log.warn(
                    "Gemini question generation request error: model={} endpoint={} reason={}",
                    properties.getModel(),
                    sanitizeEndpoint(properties.getApiBaseUrl()),
                    exception.getMessage(),
                    exception
            );
            throw new BusinessException(ErrorCode.AI_PROVIDER_UNAVAILABLE, "AI provider request failed");
        }
    }

    @Override
    public String providerName() {
        return PROVIDER_NAME;
    }

    @Override
    public String modelName() {
        return properties.getModel();
    }

    public String promptVersion() {
        return PROMPT_VERSION;
    }

    private void ensureAvailable() {
        if (!properties.isEnabled()) {
            throw new BusinessException(ErrorCode.AI_PROVIDER_UNAVAILABLE, "AI question generation is disabled");
        }
        if (!PROVIDER_NAME.equalsIgnoreCase(properties.getProvider())) {
            throw new BusinessException(ErrorCode.AI_PROVIDER_UNAVAILABLE, "AI question generation provider is not configured");
        }
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new BusinessException(ErrorCode.AI_PROVIDER_UNAVAILABLE, "Gemini API key is not configured");
        }
    }

    private String sendWithFallback(GenerationRequest request) {
        RestClientException lastException = null;
        List<String> models = candidateModels();
        for (int index = 0; index < models.size(); index++) {
            String model = models.get(index);
            try {
                String response = restClient
                        .post()
                        .uri("/interactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("x-goog-api-key", properties.getApiKey())
                        .header("Api-Revision", "2026-05-20")
                        .body(buildRequestBody(request, model))
                        .retrieve()
                        .body(String.class);
                if (index > 0) {
                    log.info("Gemini question generation recovered with fallback model={}", model);
                }
                return response;
            } catch (RestClientResponseException exception) {
                lastException = exception;
                log.warn(
                        "Gemini question generation attempt failed: status={} model={}",
                        exception.getStatusCode().value(),
                        model);
                if (!canTryFallback(exception) || index + 1 >= models.size()) {
                    throw exception;
                }
            } catch (RestClientException exception) {
                lastException = exception;
                log.warn(
                        "Gemini question generation transport attempt failed: model={} errorType={}",
                        model,
                        exception.getClass().getSimpleName());
                if (index + 1 >= models.size()) {
                    throw exception;
                }
            }
        }
        if (lastException != null) {
            throw lastException;
        }
        throw new BusinessException(ErrorCode.AI_PROVIDER_UNAVAILABLE, "AI provider request failed");
    }

    private List<String> candidateModels() {
        String primary = normalizeModel(properties.getModel());
        String fallback = normalizeModel(properties.getFallbackModel());
        if (fallback == null || fallback.equals(primary)) {
            return List.of(primary);
        }
        return List.of(primary, fallback);
    }

    private String normalizeModel(String value) {
        if (value == null || value.isBlank()) {
            return "gemini-3.5-flash";
        }
        String normalized = value.trim();
        return normalized.startsWith("models/")
                ? normalized.substring("models/".length())
                : normalized;
    }

    private boolean canTryFallback(RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        return status == 400 || status == 404 || status == 408 || status == 429 || status >= 500;
    }

    private static RestClient createRestClient(QuestionAiGenerationProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getTimeout());
        requestFactory.setReadTimeout(properties.getTimeout());
        return RestClient.builder()
                .baseUrl(properties.getApiBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    private Map<String, Object> buildRequestBody(GenerationRequest request, String model) {
        List<Map<String, Object>> input = new ArrayList<>();
        input.add(Map.of("type", "text", "text", buildPrompt(request)));

        Map<String, Object> responseFormat = new LinkedHashMap<>();
        responseFormat.put("type", "text");
        responseFormat.put("mime_type", "application/json");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("input", input);
        body.put("response_format", responseFormat);
        return body;
    }

    private String buildPrompt(GenerationRequest request) {
        StringBuilder sourceBuilder = new StringBuilder();
        for (SourceInput source : request.sources()) {
            sourceBuilder.append("\nSOURCE ")
                    .append(source.generationSourceId())
                    .append(" | ")
                    .append(source.sourceName())
                    .append(" | checksum=")
                    .append(source.checksum())
                    .append(" | version=")
                    .append(source.version())
                    .append('\n');
            for (ChunkInput chunk : source.chunks()) {
                sourceBuilder.append("- CHUNK ")
                        .append(chunk.chunkId())
                        .append(" | ref=")
                        .append(chunk.chunkReference())
                        .append(": ")
                        .append(chunk.excerpt())
                        .append('\n');
            }
        }

        String instruction = request.generationInstruction() == null || request.generationInstruction().isBlank()
                ? "Generate clear, grounded draft questions that assess the selected lesson materials."
                : request.generationInstruction().trim();

        return """
                You create draft questions for a human-reviewed Question Bank.
                This is not a chatbot. Use only the provided SOURCE/CHUNK content. Do not use outside knowledge.
                Output language: %s.
                Requested count: %d.
                Allowed question types: %s.
                User generation instruction: %s.

                Return strict JSON only, no markdown, with this shape:
                {
                  "questions": [
                    {
                      "questionText": "...",
                      "questionType": "multiple_choice" or "true_false",
                      "answers": [{"answerText":"...","correct":true|false,"orderIndex":1}],
                      "explanation": "..." or null,
                      "evidence": [
                        {
                          "generationSourceId": "uuid from SOURCE",
                          "chunkId": "uuid from CHUNK",
                          "chunkReference": "ref from CHUNK",
                          "excerpt": "short excerpt from the provided chunk",
                          "supportsCorrectAnswer": true
                        }
                      ]
                    }
                  ]
                }

                Rules:
                - Every question must have exactly one correct answer.
                - multiple_choice must have exactly 4 answers.
                - true_false must have exactly two answers: True and False.
                - At least one evidence item must support the correct answer.
                - If the provided chunks are insufficient, return fewer questions rather than hallucinating.

                Sources:
                %s
                """.formatted(
                request.language(),
                request.requestedCount(),
                String.join(", ", request.questionTypes()),
                instruction,
                sourceBuilder
        );
    }

    private GenerationResult parseResponse(String response) throws IOException {
        JsonNode root = objectMapper.readTree(response == null ? "{}" : response);
        String outputText = extractOutputText(root);
        if (outputText == null || outputText.isBlank()) {
            throw new IOException("Missing Gemini output text");
        }
        String json = stripJsonFence(outputText);
        GeminiPayload payload = objectMapper.readValue(json, GeminiPayload.class);
        return new GenerationResult(
                payload.questions() == null ? List.of() : payload.questions(),
                null,
                null,
                null
        );
    }

    private String extractOutputText(JsonNode root) {
        String direct = text(root, "output_text");
        if (direct != null) return direct;
        direct = text(root, "outputText");
        if (direct != null) return direct;
        return findTextValue(root);
    }

    private String findTextValue(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (node.isObject()) {
            String text = text(node, "text");
            if (text != null) return text;
            var fields = node.fields();
            while (fields.hasNext()) {
                String found = findTextValue(fields.next().getValue());
                if (found != null) return found;
            }
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                String found = findTextValue(child);
                if (found != null) return found;
            }
        }
        return null;
    }

    private String text(JsonNode node, String fieldName) {
        if (node == null) return null;
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull()) return null;
        return value.asText(null);
    }

    private String stripJsonFence(String value) {
        String trimmed = value.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(?:json)?", "").trim();
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3).trim();
            }
        }
        return trimmed;
    }

    private String sanitizeEndpoint(String value) {
        if (value == null || value.isBlank()) {
            return "<blank>";
        }
        return value.replaceAll("(?i)(key=)[^&]+", "$1<redacted>");
    }

    private String truncateForLog(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "<empty>";
        }
        String normalized = value.replace('\r', ' ').replace('\n', ' ').trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...<truncated>";
    }

    private record GeminiPayload(List<QuestionGenerationProvider.GeneratedQuestion> questions) {
    }
}
