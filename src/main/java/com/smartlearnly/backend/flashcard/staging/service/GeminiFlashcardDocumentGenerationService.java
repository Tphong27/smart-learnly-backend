package com.smartlearnly.backend.flashcard.staging.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.flashcard.staging.service.FlashcardDocumentGenerationService.DocumentGenerationRequest;
import com.smartlearnly.backend.flashcard.staging.service.FlashcardDocumentTextExtractionService.DocumentImage;
import com.smartlearnly.backend.flashcard.staging.service.FlashcardTextGenerationService.GeneratedFlashcardCandidate;
import com.smartlearnly.backend.flashcard.staging.service.FlashcardTextGenerationService.GenerationResult;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Service
public class GeminiFlashcardDocumentGenerationService implements FlashcardDocumentGenerationService {
    private static final String PROVIDER_NAME = "gemini";
    private static final String SOURCE_TYPE_TEXT = "TEXT";
    private static final String SOURCE_TYPE_PDF = "PDF";
    private static final int MIN_CONTENT_LENGTH = 100;
    private static final int MAX_CONTENT_LENGTH = 20000;
    private static final int MAX_FRONT_TEXT_LENGTH = 2000;
    private static final int MAX_BACK_TEXT_LENGTH = 4000;
    private static final int MAX_HINT_LENGTH = 1000;
    private static final int MAX_EXPLANATION_LENGTH = 6000;
    private static final int MAX_SOURCE_EXCERPT_LENGTH = 1000;
    private static final Set<String> SUPPORTED_LANGUAGE_OPTIONS = Set.of("auto", "vi", "en");

    private final FlashcardDocumentGenerationProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestClient restClient;

    @Autowired
    public GeminiFlashcardDocumentGenerationService(
            FlashcardDocumentGenerationProperties properties) {
        this(properties, createRestClient(properties));
    }

    GeminiFlashcardDocumentGenerationService(
            FlashcardDocumentGenerationProperties properties,
            RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    @Override
    public GenerationResult generate(DocumentGenerationRequest request) {
        long startedAtNanos = System.nanoTime();
        int geminiCalls = 0;
        String selectableText = normalizeDocumentText(request == null ? null : request.documentText());
        String sourceType = request == null ? null : request.sourceType();
        String language = request == null ? "auto" : request.language();
        List<DocumentImage> imageInputs = selectableText.length() < MIN_CONTENT_LENGTH
                ? validImages(request == null ? List.of() : request.images())
                : List.of();
        List<DocumentImage> renderedPageInputs = selectableText.length() < MIN_CONTENT_LENGTH
                && SOURCE_TYPE_PDF.equalsIgnoreCase(normalizeNullable(sourceType))
                ? validPageImages(request == null ? List.of() : request.renderedPageImages())
                : List.of();
        try {
            if (selectableText.length() < MIN_CONTENT_LENGTH && imageInputs.isEmpty() && renderedPageInputs.isEmpty()) {
                validateMergedContent(selectableText, sourceType);
            }
            ensureAvailable();
            List<ImageInsight> imageInsights = List.of();
            if (!imageInputs.isEmpty()) {
                geminiCalls += 1;
                imageInsights = describeImagesSafely(
                        selectableText,
                        imageInputs,
                        buildImageInsightPrompt(),
                        "embedded document image text extraction",
                        "Embedded image"
                );
            }
            String mergedContent = mergeContent(selectableText, imageInsights);
            if (!hasReadableContent(mergedContent) && !renderedPageInputs.isEmpty()) {
                geminiCalls += 1;
                List<ImageInsight> pageInsights = describeImagesSafely(
                        selectableText,
                        renderedPageInputs,
                        buildRenderedPageInsightPrompt(),
                        "rendered PDF page text extraction",
                        "Rendered PDF page"
                );
                mergedContent = mergeContent(selectableText, combineInsights(imageInsights, pageInsights));
            }
            mergedContent = validateMergedContent(mergedContent, sourceType);
            geminiCalls += 1;
            String outputText = sendGeminiInput(buildGenerationInput(request, mergedContent), "flashcard document generation");
            return parseGenerationOutput(outputText, request == null ? 0 : request.desiredCount(), language);
        }
        finally {
            log.info(
                    "Flashcard document generation completed: sourceType={} selectedTextLength={} embeddedImagesConsidered={} pagesRendered={} geminiCalls={} elapsedMs={}",
                    normalizeNullable(sourceType),
                    selectableText.length(),
                    imageInputs.size(),
                    renderedPageInputs.size(),
                    geminiCalls,
                    elapsedMillis(startedAtNanos)
            );
        }
    }

    private List<ImageInsight> describeImagesSafely(
            String selectableText,
            List<DocumentImage> images,
            String prompt,
            String operation,
            String itemLabel
    ) {
        try {
            return describeImages(images, prompt, operation, itemLabel);
        }
        catch (BusinessException exception) {
            if (selectableText.length() >= MIN_CONTENT_LENGTH) {
                log.warn(
                        "Skipping flashcard document image text extraction because selectable text is sufficient: operation={} reason={}",
                        operation,
                        exception.getMessage()
                );
                return List.of();
            }
            throw exception;
        }
    }

    private List<ImageInsight> describeImages(
            List<DocumentImage> images,
            String prompt,
            String operation,
            String itemLabel
    ) {
        List<DocumentImage> imageInputs = images == null ? List.of() : images;
        if (imageInputs.isEmpty()) {
            return List.of();
        }
        String outputText = sendGeminiInput(buildImageInsightInput(imageInputs, prompt, itemLabel), operation);
        return parseImageInsightOutput(outputText);
    }

    private List<DocumentImage> validImages(List<DocumentImage> images) {
        return validImages(images, properties.getMaxEmbeddedImages(), properties.getMaxEmbeddedImageSize().toBytes());
    }

    private List<DocumentImage> validPageImages(List<DocumentImage> images) {
        return validImages(images, properties.getMaxRenderedPdfPages(), properties.getMaxRenderedPageImageSize().toBytes());
    }

    private List<DocumentImage> validImages(List<DocumentImage> images, int maxImages, long maxImageBytes) {
        if (images == null || images.isEmpty()) {
            return List.of();
        }
        List<DocumentImage> valid = new ArrayList<>();
        for (DocumentImage image : images) {
            if (valid.size() >= Math.max(0, maxImages)) {
                break;
            }
            if (image == null || image.content() == null || image.content().length == 0) {
                continue;
            }
            if (image.content().length > Math.max(1L, maxImageBytes)) {
                continue;
            }
            String contentType = normalizeNullable(image.contentType());
            if (contentType == null || !properties.getAllowedImageContentTypes().contains(contentType.toLowerCase(Locale.ROOT))) {
                continue;
            }
            valid.add(image);
        }
        return valid;
    }

    private List<Map<String, Object>> buildImageInsightInput(
            List<DocumentImage> images,
            String prompt,
            String itemLabel
    ) {
        List<Map<String, Object>> input = new ArrayList<>();
        input.add(Map.of("type", "text", "text", prompt));
        for (int index = 0; index < images.size(); index += 1) {
            DocumentImage image = images.get(index);
            input.add(Map.of(
                    "type", "text",
                    "text", itemLabel + " " + (index + 1) + ": " + safeFileName(image.fileName(), index + 1)
            ));
            input.add(Map.of(
                    "type", "image",
                    "data", Base64.getEncoder().encodeToString(image.content()),
                    "mime_type", image.contentType()
            ));
        }
        return input;
    }

    private List<Map<String, Object>> buildGenerationInput(DocumentGenerationRequest request, String mergedContent) {
        int desiredCount = Math.max(1, request == null ? 10 : request.desiredCount());
        String language = request == null ? "auto" : request.language();
        String difficulty = request == null ? "medium" : request.difficulty();
        String sourceType = request == null ? null : request.sourceType();
        String sourceName = request == null ? null : request.sourceName();
        return List.of(
                Map.of("type", "text", "text", buildGenerationPrompt(language, difficulty, desiredCount, sourceType, sourceName)),
                Map.of("type", "text", "text", "Document content:\n" + mergedContent)
        );
    }

    String buildGenerationPrompt(String language, String difficulty, int desiredCount, String sourceType, String sourceName) {
        String normalizedLanguage = normalizeLanguage(language);
        String languageInstruction = switch (normalizedLanguage) {
            case "vi" -> "Target language: Vietnamese. Generate every flashcard in natural Vietnamese. Translate or summarize English or mixed source content into Vietnamese. sourceExcerpt must also be Vietnamese, translated or paraphrased from the document if needed.";
            case "en" -> "Target language: English. Generate every flashcard in natural English. Translate or summarize Vietnamese or mixed source content into English. sourceExcerpt must also be English, translated or paraphrased from the document if needed.";
            default -> "Target language: Auto-detect. Detect the document's main language and generate every flashcard in that detected language. sourceExcerpt may follow the main document language.";
        };
        String normalizedDifficulty = normalizeDifficulty(difficulty);
        String sourceLabel = normalizeNullable(sourceType) == null ? "document" : sourceType.trim();
        String nameLabel = normalizeNullable(sourceName) == null ? "uploaded file" : sourceName.trim();

        return """
                You create high-quality flashcards from uploaded document content.
                Source type: %s.
                Source name: %s.
                Maximum target cards: %d.
                Difficulty: %s.
                %s

                Return strict JSON only, no markdown, no code fences, with exactly this shape:
                {
                  "cards": [
                    {
                      "frontText": "...",
                      "backText": "...",
                      "hint": null,
                      "explanation": null,
                      "sourceExcerpt": null
                    }
                  ]
                }

                Rules:
                - Respect the selected Language.
                - Do not mix Vietnamese and English except for unavoidable technical terms, names, code, API names, annotations, or keywords.
                - For Vietnamese output, explanations and answers must be natural Vietnamese.
                - For English output, explanations and answers must be natural English.
                - For Auto-detect, follow the main document language.
                - For Vietnamese or English targets, sourceExcerpt must use the selected language; translate or paraphrase the supporting source as needed.
                - If you cannot provide sourceExcerpt in the selected language, set sourceExcerpt to null rather than mixing languages.
                - Keep technical terms, names, annotations, API names, code, class names, and keywords unchanged when needed.
                - Generate up to the maximum target cards; fewer cards are correct when the document has fewer supported facts.
                - Every card must be grounded in the provided document content.
                - Do not invent extra cards to reach the maximum target.
                - Do not use outside knowledge, including Spring Boot knowledge, unless it is explicitly present in the document.
                - Generate concrete useful cards: concepts, definitions, Q/A, process steps, comparisons, and key facts.
                - Avoid vague cards like "What is the key idea..." unless the document genuinely requires a high-level summary.
                - Keep backText answer-focused; put longer details in explanation.
                - Use hint only when it helps review without giving away the answer.
                - Use sourceExcerpt for a short supporting source line whenever possible.
                - If a candidate card has no clear source support, omit it.
                - Avoid duplicate cards and avoid repeating the same fact with different wording.
                """.formatted(sourceLabel, nameLabel, desiredCount, normalizedDifficulty, languageInstruction);
    }

    String buildImageInsightPrompt() {
        return """
                Read embedded document images to support flashcard creation.
                Return strict JSON only, no markdown, no code fences, with this shape:
                {
                  "images": [
                    {
                      "fileName": "...",
                      "ocrText": "...",
                      "description": "..."
                    }
                  ]
                }
                Rules:
                - Preserve visible text in ocrText when legible.
                - Use description for charts, diagrams, screenshots, formulas, tables, or other educational content.
                - Do not create flashcards here.
                - If an image has no useful educational content, use empty strings.
                """;
    }

    String buildRenderedPageInsightPrompt() {
        return """
                Read rendered PDF page images to recover readable document content for flashcard creation.
                Return strict JSON only, no markdown, no code fences, with this shape:
                {
                  "images": [
                    {
                      "fileName": "...",
                      "ocrText": "...",
                      "description": "..."
                    }
                  ]
                }
                Rules:
                - Preserve visible text in ocrText when legible.
                - Use description only for diagrams, charts, tables, screenshots, formulas, or other educational content.
                - Do not create flashcards here.
                - If a page has no useful educational content, use empty strings.
                """;
    }

    String sendGeminiInput(List<Map<String, Object>> input, String operation) {
        RestClientException lastException = null;
        List<String> models = candidateModels();
        for (int index = 0; index < models.size(); index++) {
            String model = models.get(index);
            try {
                String output = sendGeminiInputOnce(input, model, operation);
                if (index > 0) {
                    log.info(
                            "Gemini flashcard document {} recovered with fallback model={}",
                            operation,
                            model);
                }
                return output;
            }
            catch (RestClientResponseException exception) {
                lastException = exception;
                log.warn(
                        "Gemini flashcard document {} attempt failed: status={} model={}",
                        operation,
                        exception.getStatusCode().value(),
                        model);
                if (!canTryFallback(exception) || index + 1 >= models.size()) {
                    throw toProviderHttpException(exception);
                }
            }
            catch (RestClientException exception) {
                lastException = exception;
                log.warn(
                        "Gemini flashcard document {} transport attempt failed: model={} errorType={}",
                        operation,
                        model,
                        exception.getClass().getSimpleName());
                if (index + 1 >= models.size()) {
                    throw new BusinessException(
                            ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                            "Document could not be processed right now. Please try again later.");
                }
            }
        }
        if (lastException instanceof RestClientResponseException responseException) {
            throw toProviderHttpException(responseException);
        }
        throw new BusinessException(
                ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                "Document could not be processed right now. Please try again later.");
    }

    private String sendGeminiInputOnce(
            List<Map<String, Object>> input,
            String model,
            String operation) {
        try {
            String response = restClient
                    .post()
                    .uri("/interactions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("x-goog-api-key", properties.getApiKey())
                    .header("Api-Revision", "2026-05-20")
                    .body(buildRequestBody(input, model))
                    .retrieve()
                    .body(String.class);
            String outputText = extractOutputText(objectMapper.readTree(response == null ? "{}" : response));
            if (outputText == null || outputText.isBlank()) {
                throw new IOException("Missing output text");
            }
            return outputText;
        }
        catch (IOException | IllegalArgumentException exception) {
            log.warn(
                    "Gemini flashcard document {} response parse error: model={} endpoint={} errorType={}",
                    operation,
                    model,
                    sanitizeEndpoint(properties.getApiBaseUrl()),
                    exception.getClass().getSimpleName()
            );
            throw new BusinessException(
                    ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                    "Document generation returned an invalid response. Please try again."
            );
        }
    }

    private Map<String, Object> buildRequestBody(List<Map<String, Object>> input, String model) {
        Map<String, Object> responseFormat = new LinkedHashMap<>();
        responseFormat.put("type", "text");
        responseFormat.put("mime_type", "application/json");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("input", input == null ? List.of() : input);
        body.put("response_format", responseFormat);
        body.put("store", false);
        return body;
    }

    private List<String> candidateModels() {
        String primary = normalizeModel(properties.getModel(), "gemini-3.5-flash");
        String fallback = normalizeModel(properties.getFallbackModel(), null);
        if (fallback == null || fallback.equals(primary)) {
            return List.of(primary);
        }
        return List.of(primary, fallback);
    }

    private String normalizeModel(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
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

    private static RestClient createRestClient(FlashcardDocumentGenerationProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getTimeout());
        requestFactory.setReadTimeout(properties.getTimeout());
        return RestClient.builder()
                .baseUrl(properties.getApiBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    BusinessException toProviderHttpException(RestClientResponseException exception) {
        if (isProviderLimitException(exception)) {
            return new BusinessException(
                    ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                    "Document image reading is temporarily unavailable. Please try again later or use a text-based document."
            );
        }
        return new BusinessException(
                ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                "Document could not be processed right now. Please try again later."
        );
    }

    private boolean isProviderLimitException(RestClientResponseException exception) {
        if (exception == null) {
            return false;
        }
        if (exception.getStatusCode().value() == 429) {
            return true;
        }
        String responseBody = exception.getResponseBodyAsString();
        String normalizedBody = responseBody == null ? "" : responseBody.toLowerCase(Locale.ROOT);
        return normalizedBody.contains("too_many_requests")
                || normalizedBody.contains("quota")
                || normalizedBody.contains("rate limit")
                || normalizedBody.contains("resource_exhausted");
    }

    private void ensureAvailable() {
        if (!properties.isEnabled()) {
            log.warn("Gemini flashcard document generation is disabled by configuration");
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE, "Document generation is unavailable");
        }
        if (!PROVIDER_NAME.equalsIgnoreCase(properties.getProvider())) {
            log.warn("Gemini flashcard document provider mismatch: configuredProvider={}", properties.getProvider());
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE, "Document generation provider is not configured");
        }
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            log.warn("Gemini flashcard document API key is missing");
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE, "Document generation is not configured");
        }
    }

    GenerationResult parseGenerationOutput(String outputText, int desiredCount) {
        return parseGenerationOutput(outputText, desiredCount, "auto");
    }

    GenerationResult parseGenerationOutput(String outputText, int desiredCount, String language) {
        GeminiCardsPayload payload = readJsonOutput(
                outputText,
                GeminiCardsPayload.class,
                "Document generation returned invalid card data. Please try again."
        );
        List<GeneratedFlashcardCandidate> candidates = validGeneratedCards(payload.cards(), desiredCount, language);
        if (candidates.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "No usable flashcards could be created from this document."
            );
        }
        return new GenerationResult(SOURCE_TYPE_TEXT, candidates);
    }

    private List<ImageInsight> parseImageInsightOutput(String outputText) {
        ImageInsightPayload payload = readJsonOutput(
                outputText,
                ImageInsightPayload.class,
                "Embedded document images could not be read. Please try again."
        );
        if (payload.images() == null || payload.images().isEmpty()) {
            return List.of();
        }
        List<ImageInsight> insights = new ArrayList<>();
        for (ImageInsight insight : payload.images()) {
            if (insight == null) {
                continue;
            }
            String ocrText = normalizeNullable(insight.ocrText());
            String description = normalizeNullable(insight.description());
            if (ocrText == null && description == null) {
                continue;
            }
            insights.add(new ImageInsight(
                    normalizeNullable(insight.fileName()),
                    ocrText,
                    description
            ));
        }
        return insights;
    }

    private <T> T readJsonOutput(String outputText, Class<T> payloadType, String invalidMessage) {
        String trimmed = outputText == null ? "" : outputText.trim();
        if (trimmed.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, invalidMessage);
        }
        try {
            return objectMapper.readValue(trimmed, payloadType);
        }
        catch (IOException strictException) {
            String unfenced = stripJsonFence(trimmed);
            if (!unfenced.equals(trimmed)) {
                try {
                    return objectMapper.readValue(unfenced, payloadType);
                }
                catch (IOException ignored) {
                    // Fall through to the clean user-facing error.
                }
            }
            throw new BusinessException(ErrorCode.INVALID_REQUEST, invalidMessage);
        }
    }

    private List<GeneratedFlashcardCandidate> validGeneratedCards(
            List<GeminiCardPayload> cards,
            int desiredCount,
            String language
    ) {
        if (cards == null || cards.isEmpty() || desiredCount <= 0) {
            return List.of();
        }
        String normalizedLanguage = normalizeLanguage(language);
        List<GeneratedFlashcardCandidate> valid = new ArrayList<>();
        Set<String> seen = new java.util.HashSet<>();
        for (GeminiCardPayload card : cards) {
            if (card == null) {
                continue;
            }
            String frontText = normalizeNullable(card.frontText());
            String backText = normalizeNullable(card.backText());
            if (frontText == null || backText == null) {
                continue;
            }
            String sourceExcerpt = normalizeNullable(card.sourceExcerpt());
            sourceExcerpt = sanitizeSourceExcerptForLanguage(sourceExcerpt, normalizedLanguage);
            if (sourceExcerpt == null && "auto".equals(normalizedLanguage)) {
                continue;
            }
            if (frontText.length() > MAX_FRONT_TEXT_LENGTH || backText.length() > MAX_BACK_TEXT_LENGTH) {
                continue;
            }
            String duplicateKey = duplicateKey(frontText, backText);
            if (!seen.add(duplicateKey)) {
                continue;
            }
            valid.add(new GeneratedFlashcardCandidate(
                    frontText,
                    backText,
                    normalizeOptionalMax(card.hint(), MAX_HINT_LENGTH),
                    normalizeOptionalMax(card.explanation(), MAX_EXPLANATION_LENGTH),
                    normalizeOptionalMax(sourceExcerpt, MAX_SOURCE_EXCERPT_LENGTH)
            ));
            if (valid.size() >= desiredCount) {
                break;
            }
        }
        return valid;
    }

    private String validateMergedContent(String value, String sourceType) {
        String normalized = normalizeDocumentText(value);
        if (normalized.length() < MIN_CONTENT_LENGTH) {
            if (SOURCE_TYPE_PDF.equalsIgnoreCase(normalizeNullable(sourceType))) {
                throw new BusinessException(
                        ErrorCode.INVALID_REQUEST,
                        "Uploaded PDF does not contain enough readable text to create flashcards. Scanned PDF pages could not be read."
                );
            }
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Uploaded document did not contain enough readable text to create flashcards."
            );
        }
        return trimToMax(normalized, MAX_CONTENT_LENGTH);
    }

    private boolean hasReadableContent(String value) {
        return normalizeDocumentText(value).length() >= MIN_CONTENT_LENGTH;
    }

    private List<ImageInsight> combineInsights(List<ImageInsight> first, List<ImageInsight> second) {
        List<ImageInsight> combined = new ArrayList<>();
        if (first != null) {
            combined.addAll(first);
        }
        if (second != null) {
            combined.addAll(second);
        }
        return List.copyOf(combined);
    }

    private String mergeContent(String selectableText, List<ImageInsight> imageInsights) {
        List<String> blocks = new ArrayList<>();
        String text = normalizeNullable(selectableText);
        if (text != null) {
            blocks.add(text);
        }
        if (imageInsights != null && !imageInsights.isEmpty()) {
            List<String> imageBlocks = new ArrayList<>();
            for (int index = 0; index < imageInsights.size(); index += 1) {
                ImageInsight insight = imageInsights.get(index);
                StringBuilder builder = new StringBuilder("Embedded image ").append(index + 1);
                if (normalizeNullable(insight.fileName()) != null) {
                    builder.append(" (").append(insight.fileName().trim()).append(")");
                }
                builder.append(":");
                if (normalizeNullable(insight.ocrText()) != null) {
                    builder.append("\nOCR text: ").append(insight.ocrText().trim());
                }
                if (normalizeNullable(insight.description()) != null) {
                    builder.append("\nDescription: ").append(insight.description().trim());
                }
                imageBlocks.add(builder.toString());
            }
            if (!imageBlocks.isEmpty()) {
                blocks.add("Document image content:\n" + String.join("\n\n", imageBlocks));
            }
        }
        return String.join("\n\n", blocks);
    }

    private String sanitizeSourceExcerptForLanguage(String sourceExcerpt, String language) {
        if (sourceExcerpt == null || "auto".equals(language)) {
            return sourceExcerpt;
        }
        if ("vi".equals(language) && looksLikeEnglishSentence(sourceExcerpt)) {
            return null;
        }
        if ("en".equals(language) && containsVietnameseCharacters(sourceExcerpt)) {
            return null;
        }
        return sourceExcerpt;
    }

    private boolean looksLikeEnglishSentence(String value) {
        if (containsVietnameseCharacters(value)) {
            return false;
        }
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", " ");
        String[] words = normalized.trim().split("\\s+");
        if (words.length < 3) {
            return false;
        }
        Set<String> commonEnglishWords = Set.of(
                "a", "an", "and", "are", "as", "at", "by", "for", "from", "in",
                "is", "it", "of", "on", "or", "that", "the", "this", "to", "with",
                "document", "service", "request", "response", "creates", "generated", "imported"
        );
        int commonCount = 0;
        for (String word : words) {
            if (commonEnglishWords.contains(word)) {
                commonCount += 1;
            }
        }
        return commonCount >= 2;
    }

    private boolean containsVietnameseCharacters(String value) {
        return value != null && value.matches(".*[ÀÁÂÃÈÉÊÌÍÒÓÔÕÙÚĂĐĨŨƠƯàáâãèéêìíòóôõùúăđĩũơưẠ-ỹ].*");
    }

    private String normalizeDocumentText(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace('\u00a0', ' ');
        String[] paragraphs = normalized.split("\\n\\s*\\n");
        List<String> blocks = new ArrayList<>();
        for (String paragraph : paragraphs) {
            String block = paragraph.replaceAll("[\\t\\x0B\\f ]+", " ")
                    .replaceAll(" *\\n *", "\n")
                    .trim();
            if (!block.isBlank()) {
                blocks.add(block);
            }
        }
        return String.join("\n\n", blocks);
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

    private String normalizeLanguage(String value) {
        String normalized = normalizeNullable(value);
        if (normalized == null) {
            return "auto";
        }
        normalized = normalized.trim().toLowerCase(Locale.ROOT);
        return SUPPORTED_LANGUAGE_OPTIONS.contains(normalized) ? normalized : "auto";
    }

    private String normalizeDifficulty(String value) {
        String normalized = normalizeNullable(value);
        return normalized == null ? "medium" : normalized.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeOptionalMax(String value, int maxLength) {
        String normalized = normalizeNullable(value);
        return normalized == null ? null : trimToMax(normalized, maxLength);
    }

    private String trimToMax(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        int end = value.lastIndexOf(' ', maxLength);
        if (end < Math.min(MIN_CONTENT_LENGTH, maxLength)) {
            end = maxLength;
        }
        return value.substring(0, end).trim();
    }

    private String duplicateKey(String frontText, String backText) {
        return normalizeForDuplicate(frontText) + "\n" + normalizeForDuplicate(backText);
    }

    private String normalizeForDuplicate(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String safeFileName(String value, int index) {
        String normalized = normalizeNullable(value);
        return normalized == null ? "embedded-image-" + index : normalized;
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

    private long elapsedMillis(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000L;
    }

    private record GeminiCardsPayload(List<GeminiCardPayload> cards) {
    }

    private record GeminiCardPayload(
            String frontText,
            String backText,
            String hint,
            String explanation,
            String sourceExcerpt
    ) {
    }

    private record ImageInsightPayload(List<ImageInsight> images) {
    }

    private record ImageInsight(
            String fileName,
            String ocrText,
            String description
    ) {
    }
}
