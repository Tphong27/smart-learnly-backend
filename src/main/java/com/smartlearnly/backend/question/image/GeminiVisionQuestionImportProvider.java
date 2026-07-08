package com.smartlearnly.backend.question.image;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.question.dto.QuestionImageImportDtos;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiVisionQuestionImportProvider implements ImageQuestionImportProvider {
    private static final String PROVIDER_NAME = "gemini";

    private final QuestionImageImportProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public ImageImportParseResult parse(ImageImportRequest request) {
        ensureAvailable();
        try {
            String response = restClient()
                    .post()
                    .uri("/interactions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("x-goog-api-key", properties.getApiKey())
                    .body(buildRequestBody(request))
                    .retrieve()
                    .body(String.class);
            return parseResponse(response);
        }
        catch (RestClientResponseException exception) {
            log.warn(
                    "Gemini image import HTTP error: status={} model={} endpoint={} responseBody={}",
                    exception.getStatusCode().value(),
                    properties.getModel(),
                    sanitizeEndpoint(properties.getApiBaseUrl()),
                    truncateForLog(exception.getResponseBodyAsString(), 1600),
                    exception
            );
            throw new BusinessException(
                    ErrorCode.IMAGE_IMPORT_UNAVAILABLE,
                    "Gemini image import provider returned HTTP " + exception.getStatusCode().value()
            );
        }
        catch (IOException | IllegalArgumentException exception) {
            log.warn(
                    "Gemini image import response parse error: model={} endpoint={} reason={}",
                    properties.getModel(),
                    sanitizeEndpoint(properties.getApiBaseUrl()),
                    exception.getMessage(),
                    exception
            );
            throw new BusinessException(
                    ErrorCode.IMAGE_IMPORT_UNAVAILABLE,
                    "Gemini image import provider returned an invalid response"
            );
        }
        catch (RestClientException exception) {
            log.warn(
                    "Gemini image import request error: model={} endpoint={} reason={}",
                    properties.getModel(),
                    sanitizeEndpoint(properties.getApiBaseUrl()),
                    exception.getMessage(),
                    exception
            );
            throw new BusinessException(
                    ErrorCode.IMAGE_IMPORT_UNAVAILABLE,
                    "Gemini image import provider request failed"
            );
        }
    }

    private void ensureAvailable() {
        if (!properties.isEnabled()) {
            log.warn("Gemini image import is disabled by configuration");
            throw new BusinessException(ErrorCode.IMAGE_IMPORT_UNAVAILABLE, "Image import is disabled");
        }
        if (!PROVIDER_NAME.equalsIgnoreCase(properties.getProvider())) {
            log.warn("Gemini image import provider mismatch: configuredProvider={}", properties.getProvider());
            throw new BusinessException(ErrorCode.IMAGE_IMPORT_UNAVAILABLE, "Gemini image import provider is not configured");
        }
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            log.warn("Gemini image import API key is missing");
            throw new BusinessException(ErrorCode.IMAGE_IMPORT_UNAVAILABLE, "Gemini API key is not configured");
        }
    }

    private RestClient restClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getTimeout());
        requestFactory.setReadTimeout(properties.getTimeout());
        return RestClient.builder()
                .baseUrl(properties.getApiBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    private Map<String, Object> buildRequestBody(ImageImportRequest request) {
        List<Map<String, Object>> input = new ArrayList<>();
        input.add(Map.of("type", "text", "text", buildPrompt(request.language())));
        for (int index = 0; index < request.files().size(); index += 1) {
            ImageImportFile file = request.files().get(index);
            input.add(Map.of(
                    "type", "text",
                    "text", "File " + (index + 1) + ": " + file.fileName()
            ));
            input.add(Map.of(
                    "type", "image",
                    "data", Base64.getEncoder().encodeToString(file.content()),
                    "mime_type", file.contentType()
            ));
        }

        Map<String, Object> responseFormat = new LinkedHashMap<>();
        responseFormat.put("type", "text");
        responseFormat.put("mime_type", "application/json");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getModel());
        body.put("input", input);
        body.put("response_format", responseFormat);
        return body;
    }

    private String buildPrompt(String language) {
        String targetLanguage = language == null || language.isBlank() ? "vi" : language.trim();
        return """
                You are an OCR and question parsing assistant for a Question Bank import preview.
                Language hint: %s.
                Return strict JSON only, no markdown, with this shape:
                {
                  "questions": [
                    {
                      "questionText": "...",
                      "questionType": "multiple_choice" or "true_false",
                      "answers": [{"answerText":"...","correct":true|false}],
                      "difficulty": 1-5 or null,
                      "explanation": "..." or null,
                      "warnings": [],
                      "errors": []
                    }
                  ],
                  "warnings": []
                }
                Do not include full OCR text by default. Only return parsed questions and warnings.
                Rules:
                - Do not create questions that are not present in the images.
                - Preserve question order in the uploaded file.
                - Only mark an answer correct if the image explicitly provides the answer key.
                - If the answer key is missing or ambiguous, include the question with no correct answer and add an error.
                - If explanation/rationale is clearly present in the image, copy it into explanation.
                - If explanation/rationale is not present, set explanation to null. Do not write a new explanation.
                - Use multiple_choice for A/B/C/D style questions and true_false only for True/False questions.
                """.formatted(targetLanguage);
    }

    private ImageImportParseResult parseResponse(String response) throws IOException {
        JsonNode root = objectMapper.readTree(response == null ? "{}" : response);
        String outputText = extractOutputText(root);
        if (outputText == null || outputText.isBlank()) {
            throw new IOException("Missing Gemini output text");
        }
        String json = stripJsonFence(outputText);
        GeminiPayload payload = objectMapper.readValue(json, GeminiPayload.class);
        return new ImageImportParseResult(
                payload.ocrText() == null ? "" : payload.ocrText(),
                payload.questions() == null ? List.of() : payload.questions(),
                payload.warnings() == null ? List.of() : payload.warnings()
        );
    }

    private String extractOutputText(JsonNode root) {
        String direct = text(root, "output_text");
        if (direct != null) return direct;
        direct = text(root, "outputText");
        if (direct != null) return direct;
        JsonNode output = root.get("output");
        String fromOutput = findTextValue(output);
        if (fromOutput != null) return fromOutput;
        JsonNode candidates = root.get("candidates");
        String fromCandidates = findTextValue(candidates);
        if (fromCandidates != null) return fromCandidates;
        JsonNode steps = root.get("steps");
        String fromSteps = findTextValue(steps);
        if (fromSteps != null) return fromSteps;
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

    private record GeminiPayload(
            String ocrText,
            List<QuestionImageImportDtos.PreviewQuestion> questions,
            List<String> warnings
    ) {
    }
}
