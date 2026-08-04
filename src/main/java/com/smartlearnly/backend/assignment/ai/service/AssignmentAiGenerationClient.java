package com.smartlearnly.backend.assignment.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlearnly.backend.admin.settings.service.SystemSettingsService;
import com.smartlearnly.backend.admin.settings.service.SystemSettingsService.AssignmentAiSettings;
import com.smartlearnly.backend.assignment.ai.config.AssignmentAiDraftProperties;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** Gọi Gemini cho Assignment AI mà không chứa quy tắc soạn nội dung nghiệp vụ. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssignmentAiGenerationClient {
    private static final String PROVIDER_NAME = "gemini";

    private final AssignmentAiDraftProperties properties;
    private final SystemSettingsService settingsService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Gửi prompt tới Gemini với đúng model fallback và retry hiện hành.
     * Trả về text đã bóc từ response hoặc ném lỗi nghiệp vụ khi provider không sẵn sàng.
     */
    public String generate(List<Map<String, Object>> input) {
        RestClientResponseException lastHttpException = null;
        for (String model : candidateModels()) {
            for (int attempt = 1; attempt <= 2; attempt += 1) {
                try {
                    String response = generateOnce(input, model);
                    if (attempt > 1 || !model.equals(modelName())) {
                        log.info(
                                "Gemini assignment draft succeeded: attempt={} configuredModel={} effectiveModel={}",
                                attempt,
                                modelName(),
                                model
                        );
                    }
                    return response;
                }
                catch (RestClientResponseException exception) {
                    lastHttpException = exception;
                    if (!isRetryableProviderException(exception)) {
                        break;
                    }
                    if (attempt < 2) {
                        log.warn(
                                "Retrying Gemini assignment draft after provider HTTP {}: attempt={} model={}",
                                exception.getStatusCode().value(),
                                attempt,
                                model
                        );
                        sleepBeforeRetry();
                    }
                }
            }
            if (!isRetryableProviderException(lastHttpException)) {
                break;
            }
            if (!model.equals(fallbackModel())) {
                log.warn(
                        "Falling back Gemini assignment draft model after HTTP {}: from={} to={}",
                        lastHttpException.getStatusCode().value(),
                        model,
                        fallbackModel()
                );
            }
        }

        if (lastHttpException != null) {
            handleGeminiHttpException(lastHttpException);
        }
        throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE, "AI draft could not be generated right now.");
    }

    /** Kiểm tra provider, API key và trạng thái bật trước khi bắt đầu generation. */
    public void ensureAvailable() {
        AssignmentAiSettings settings = resolveSettings();
        if (!settings.enabled()
                || !PROVIDER_NAME.equalsIgnoreCase(settings.provider())
                || settings.apiKey() == null
                || settings.apiKey().isBlank()) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE, "AI draft generation is not configured.");
        }
        String apiKey = settings.apiKey().trim();
        if (apiKey.startsWith("<") || apiKey.endsWith(">")) {
            throw new BusinessException(
                    ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                    "Assignment AI API key must not include placeholder angle brackets.");
        }
    }

    /** Gửi một request generation cho model đã chọn và bóc text phản hồi. */
    private String generateOnce(List<Map<String, Object>> input, String model) {
        try {
            String response = restClient()
                    .post()
                    .uri("/models/" + model + ":generateContent")
                    .header("x-goog-api-key", assignmentApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(buildRequestBody(input))
                    .retrieve()
                    .body(String.class);
            String outputText = extractOutputText(objectMapper.readTree(response == null ? "{}" : response));
            if (outputText == null || outputText.isBlank()) {
                throw new IOException("Missing output text");
            }
            return outputText.trim();
        }
        catch (RestClientResponseException exception) {
            throw exception;
        }
        catch (IOException | IllegalArgumentException exception) {
            log.warn("Gemini assignment draft response parse error: reason={}", exception.getMessage(), exception);
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE, "AI draft returned an invalid response. Please try again.");
        }
        catch (RestClientException exception) {
            log.warn("Gemini assignment draft request error: reason={}", exception.getMessage(), exception);
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE, "AI draft service is unavailable. Please try again later.");
        }
    }

    /** Chuyển HTTP error của Gemini thành thông báo nghiệp vụ ổn định cho frontend. */
    private void handleGeminiHttpException(RestClientResponseException exception) {
        log.warn(
                "Gemini assignment draft HTTP error: status={} model={} endpoint={} responseBody={}",
                exception.getStatusCode().value(),
                modelName(),
                "/models/" + modelName() + ":generateContent",
                truncateForLog(exception.getResponseBodyAsString(), 1000),
                exception
        );
        throw new BusinessException(
                ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                isProviderLimitException(exception)
                        ? "AI draft is temporarily rate limited. Please try again later."
                        : providerErrorMessage(exception)
        );
    }

    /** Chỉ retry các lỗi tạm thời mà provider có khả năng tự phục hồi. */
    private boolean isRetryableProviderException(RestClientResponseException exception) {
        if (exception == null) {
            return false;
        }
        int status = exception.getStatusCode().value();
        return status == 503 || status == 502 || status == 504;
    }

    /** Chờ ngắn giữa hai lần gọi provider và giữ lại cờ interrupt của thread. */
    private void sleepBeforeRetry() {
        try {
            Thread.sleep(900L);
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    /** Dựng JSON request Gemini với giới hạn output token đang dùng trước refactor. */
    private Map<String, Object> buildRequestBody(List<Map<String, Object>> input) {
        String prompt = input == null
                ? ""
                : input.stream()
                .filter(Map.class::isInstance)
                .map(item -> item.get("text"))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .findFirst()
                .orElse("");

        Map<String, Object> part = new LinkedHashMap<>();
        part.put("text", prompt);

        Map<String, Object> content = new LinkedHashMap<>();
        content.put("role", "user");
        content.put("parts", List.of(part));

        Map<String, Object> generationConfig = new LinkedHashMap<>();
        generationConfig.put("maxOutputTokens", 7000);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contents", List.of(content));
        body.put("generationConfig", generationConfig);
        return body;
    }

    /** Đọc cấu hình Assignment AI đã hợp nhất từ system settings. */
    private AssignmentAiSettings resolveSettings() {
        return settingsService.resolveAssignmentAiSettings();
    }

    /** Chuẩn hóa tên model chính theo định dạng endpoint Gemini. */
    private String modelName() {
        String configured = normalizeNullable(resolveSettings().model());
        if (configured == null) {
            return "gemini-flash-latest";
        }
        return configured.startsWith("models/")
                ? configured.substring("models/".length())
                : configured;
    }

    /** Trả về model chính và fallback theo đúng thứ tự thử. */
    private List<String> candidateModels() {
        String primary = modelName();
        String fallback = fallbackModel();
        if (fallback.equals(primary)) {
            return List.of(primary);
        }
        return List.of(primary, fallback);
    }

    /** Chuẩn hóa tên model fallback theo định dạng endpoint Gemini. */
    private String fallbackModel() {
        String configured = normalizeNullable(resolveSettings().fallbackModel());
        if (configured == null) {
            return "gemini-flash-lite-latest";
        }
        return configured.startsWith("models/")
                ? configured.substring("models/".length())
                : configured;
    }

    /** Tạo HTTP client theo base URL và timeout cấu hình hiện hành. */
    private RestClient restClient() {
        AssignmentAiSettings settings = resolveSettings();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(settings.timeout());
        requestFactory.setReadTimeout(settings.timeout());
        return RestClient.builder()
                .baseUrl(properties.getApiBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    /** Lấy API key đã được validation từ system settings. */
    private String assignmentApiKey() {
        return resolveSettings().apiKey().trim();
    }

    /** Nhận diện quota/rate-limit từ status hoặc response body của provider. */
    private boolean isProviderLimitException(RestClientResponseException exception) {
        if (exception == null) return false;
        if (exception.getStatusCode().value() == 429) return true;
        String body = exception.getResponseBodyAsString();
        String normalized = body == null ? "" : body.toLowerCase(Locale.ROOT);
        return normalized.contains("quota")
                || normalized.contains("rate limit")
                || normalized.contains("too_many_requests")
                || normalized.contains("resource_exhausted");
    }

    /** Tạo thông báo cấu hình phù hợp cho từng nhóm HTTP status. */
    private String providerErrorMessage(RestClientResponseException exception) {
        if (exception == null) {
            return "AI draft could not be generated right now.";
        }
        int status = exception.getStatusCode().value();
        if (status == 400 || status == 404) {
            return "AI draft provider rejected the request. Please check APP_ASSIGNMENT_AI_MODEL and Assignment Gemini API configuration.";
        }
        if (status == 401 || status == 403) {
            return "AI draft provider rejected the API key. Please check ASSIGNMENT_AI_GEMINI_API_KEY.";
        }
        return "AI draft provider returned HTTP " + status + ". Please check backend logs for Gemini response body.";
    }

    /** Bóc text từ các dạng response Gemini được hỗ trợ. */
    private String extractOutputText(JsonNode root) {
        String direct = text(root, "output_text");
        if (direct != null) return direct;
        direct = text(root, "outputText");
        if (direct != null) return direct;
        String generatedContent = extractGenerateContentText(root);
        if (generatedContent != null) return generatedContent;
        return findTextValue(root);
    }

    /** Bóc text từ candidates/content của generateContent. */
    private String extractGenerateContentText(JsonNode root) {
        if (root == null || root.isNull()) {
            return null;
        }
        JsonNode candidates = root.get("candidates");
        if (candidates != null && candidates.isArray() && !candidates.isEmpty()) {
            String text = extractPartsText(candidates.get(0).path("content").path("parts"));
            if (text != null) {
                return text;
            }
        }
        return extractPartsText(root.path("content").path("parts"));
    }

    /** Ghép các text part theo thứ tự provider trả về. */
    private String extractPartsText(JsonNode parts) {
        if (parts == null || !parts.isArray()) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (JsonNode part : parts) {
            String value = text(part, "text");
            if (value == null || value.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append("\n");
            }
            builder.append(value);
        }
        String combined = builder.toString().trim();
        return combined.isBlank() ? null : combined;
    }

    /** Tìm fallback field `text` trong response JSON không chuẩn hóa. */
    private String findTextValue(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (node.isObject()) {
            String value = text(node, "text");
            if (value != null) return value;
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

    /** Đọc một text field nullable từ JSON node. */
    private String text(JsonNode node, String fieldName) {
        if (node == null) return null;
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull()) return null;
        return value.asText(null);
    }

    /** Chuẩn hóa chuỗi cấu hình rỗng thành null. */
    private String normalizeNullable(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    /** Rút gọn response provider trước khi ghi log để tránh log quá lớn. */
    private String truncateForLog(String value, int maxLength) {
        if (value == null || value.isBlank()) return "<empty>";
        String normalized = value.replace('\r', ' ').replace('\n', ' ').trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength) + "...<truncated>";
    }
}
