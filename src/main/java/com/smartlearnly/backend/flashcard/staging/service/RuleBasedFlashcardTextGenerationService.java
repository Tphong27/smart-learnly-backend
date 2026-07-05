package com.smartlearnly.backend.flashcard.staging.service;

import com.smartlearnly.backend.flashcard.staging.service.FlashcardTextGenerationService.GeneratedFlashcardCandidate;
import com.smartlearnly.backend.flashcard.staging.service.FlashcardTextGenerationService.GenerationRequest;
import com.smartlearnly.backend.flashcard.staging.service.FlashcardTextGenerationService.GenerationResult;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class RuleBasedFlashcardTextGenerationService implements FlashcardTextGenerationService {
    private static final String SOURCE_TYPE_TEXT = "TEXT";
    private static final String GENERATED_EXPLANATION = "Generated from pasted text.";
    private static final int MIN_CHUNK_LENGTH = 60;
    private static final int MAX_CHUNK_LENGTH = 700;
    private static final int EXCERPT_MAX_LENGTH = 140;
    private static final String DIFFICULTY_EASY = "easy";
    private static final String DIFFICULTY_HARD = "hard";
    private static final Set<String> VIETNAMESE_LANGUAGE_OPTIONS = Set.of(
            "vi",
            "vn",
            "vietnamese",
            "tiếng việt",
            "tieng viet"
    );

    private static final Pattern CARD_MARKER_PATTERN = Pattern.compile(
            "^(?:flashcard|card)\\s*\\d+\\s*[:.\\-]*\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private static final Pattern INLINE_CARD_MARKER_PATTERN = Pattern.compile(
            "\\s+(?=(?:flashcard|card)\\s*\\d+\\s*(?:[:.\\-]|\\b))",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private static final Pattern LABEL_PATTERN = Pattern.compile(
            "^(front|back|q|a|question|answer|câu hỏi|cau hoi|đáp án|dap an|trả lời|tra loi|explanation|giải thích|giai thich)\\s*[:：\\-]\\s*(.*)$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private static final Pattern INLINE_LABEL_PATTERN = Pattern.compile(
            "\\s+((?:front|back|q|a|question|answer|câu hỏi|cau hoi|đáp án|dap an|trả lời|tra loi|explanation|giải thích|giai thich))\\s*[:：\\-]\\s*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    @Override
    public GenerationResult generate(GenerationRequest request) {
        int desiredCount = Math.max(0, request.desiredCount());
        if (desiredCount == 0) {
            return new GenerationResult(SOURCE_TYPE_TEXT, List.of());
        }

        List<GeneratedFlashcardCandidate> structuredCandidates =
                structuredFlashcardCandidates(request.sourceText(), desiredCount);
        if (!structuredCandidates.isEmpty()) {
            return new GenerationResult(SOURCE_TYPE_TEXT, structuredCandidates);
        }

        String sourceText = cleanText(request.sourceText());
        List<String> chunks = chunks(sourceText, desiredCount);
        List<GeneratedFlashcardCandidate> candidates = chunks.stream()
                .limit(desiredCount)
                .map(chunk -> toCandidate(chunk, request.language(), request.difficulty()))
                .toList();
        return new GenerationResult(SOURCE_TYPE_TEXT, candidates);
    }

    private List<GeneratedFlashcardCandidate> structuredFlashcardCandidates(String sourceText, int desiredCount) {
        String parsingText = prepareStructuredParsingText(sourceText);
        if (parsingText.isBlank()) {
            return List.of();
        }

        List<GeneratedFlashcardCandidate> candidates = new ArrayList<>();
        StructuredCardBuilder currentCard = new StructuredCardBuilder();
        StructuredField currentField = null;

        String[] lines = parsingText.split("\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isBlank()) {
                continue;
            }

            if (isCardMarker(trimmed)) {
                addStructuredCandidateIfValid(candidates, currentCard, desiredCount);
                if (candidates.size() >= desiredCount) {
                    return candidates;
                }
                currentCard = new StructuredCardBuilder();
                currentField = null;
                continue;
            }

            ParsedLabel parsedLabel = parseLabel(trimmed);
            if (parsedLabel != null) {
                if (parsedLabel.field() == StructuredField.FRONT && currentCard.hasFrontAndBack()) {
                    addStructuredCandidateIfValid(candidates, currentCard, desiredCount);
                    if (candidates.size() >= desiredCount) {
                        return candidates;
                    }
                    currentCard = new StructuredCardBuilder();
                }

                currentField = parsedLabel.field();
                appendToField(currentCard, currentField, parsedLabel.value());
                continue;
            }

            if (currentField != null) {
                appendToField(currentCard, currentField, trimmed);
            }
        }

        addStructuredCandidateIfValid(candidates, currentCard, desiredCount);
        return candidates;
    }

    private String prepareStructuredParsingText(String value) {
        if (value == null) {
            return "";
        }
        String text = value.replace("\r\n", "\n").replace('\r', '\n').trim();

        // Help parse inline text such as:
        // "Flashcard 1 Front: ... Back: ..."
        text = INLINE_CARD_MARKER_PATTERN.matcher(text).replaceAll("\n");

        // Put known labels on their own lines when extracted text is compressed.
        text = INLINE_LABEL_PATTERN.matcher(text).replaceAll("\n$1: ");

        return text;
    }

    private boolean isCardMarker(String value) {
        return CARD_MARKER_PATTERN.matcher(value).matches();
    }

    private ParsedLabel parseLabel(String value) {
        Matcher matcher = LABEL_PATTERN.matcher(value);
        if (!matcher.matches()) {
            return null;
        }

        String label = matcher.group(1).toLowerCase(Locale.ROOT);
        String labelValue = matcher.group(2).trim();

        if (label.equals("front")
                || label.equals("q")
                || label.equals("question")
                || label.equals("câu hỏi")
                || label.equals("cau hoi")) {
            return new ParsedLabel(StructuredField.FRONT, labelValue);
        }

        if (label.equals("back")
                || label.equals("a")
                || label.equals("answer")
                || label.equals("đáp án")
                || label.equals("dap an")
                || label.equals("trả lời")
                || label.equals("tra loi")) {
            return new ParsedLabel(StructuredField.BACK, labelValue);
        }

        if (label.equals("explanation")
                || label.equals("giải thích")
                || label.equals("giai thich")) {
            return new ParsedLabel(StructuredField.EXPLANATION, labelValue);
        }

        return null;
    }

    private void appendToField(StructuredCardBuilder card, StructuredField field, String value) {
        String cleaned = cleanStructuredValue(value);
        if (cleaned.isBlank()) {
            return;
        }

        if (field == StructuredField.FRONT) {
            append(card.front, cleaned);
        } else if (field == StructuredField.BACK) {
            append(card.back, cleaned);
        } else if (field == StructuredField.EXPLANATION) {
            append(card.explanation, cleaned);
        }
    }

    private void append(StringBuilder builder, String value) {
        if (!builder.isEmpty()) {
            builder.append('\n');
        }
        builder.append(value);
    }

    private void addStructuredCandidateIfValid(
            List<GeneratedFlashcardCandidate> candidates,
            StructuredCardBuilder card,
            int desiredCount
    ) {
        if (candidates.size() >= desiredCount) {
            return;
        }

        String front = cleanStructuredValue(card.front.toString());
        String back = cleanStructuredValue(card.back.toString());
        String explanation = cleanStructuredValue(card.explanation.toString());

        if (front.isBlank() || back.isBlank()) {
            return;
        }

        candidates.add(new GeneratedFlashcardCandidate(
                front,
                back,
                explanation.isBlank() ? null : explanation,
                excerpt(front + " " + back)
        ));
    }

    private String cleanStructuredValue(String value) {
        return value == null
                ? ""
                : value.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace('\u00a0', ' ')
                .trim();
    }

    private List<String> chunks(String sourceText, int desiredCount) {
        if (sourceText == null || sourceText.isBlank()) {
            return List.of();
        }
        Set<String> chunks = new LinkedHashSet<>();
        addParagraphChunks(sourceText, chunks);
        if (chunks.size() < desiredCount) {
            addSentenceChunks(sourceText, chunks, desiredCount);
        }
        if (chunks.isEmpty() && sourceText.length() >= MIN_CHUNK_LENGTH) {
            chunks.add(sourceText);
        }
        return new ArrayList<>(chunks);
    }

    private void addParagraphChunks(String sourceText, Set<String> chunks) {
        String[] paragraphs = sourceText.split("\\n\\s*\\n");
        for (String paragraph : paragraphs) {
            String cleaned = cleanChunk(paragraph);
            if (isMeaningful(cleaned)) {
                chunks.add(trimToMax(cleaned));
            }
        }
    }

    private void addSentenceChunks(String sourceText, Set<String> chunks, int desiredCount) {
        String oneLine = cleanChunk(sourceText);
        String[] sentences = oneLine.split("(?<=[.!?])\\s+");
        StringBuilder builder = new StringBuilder();
        for (String sentence : sentences) {
            String cleaned = cleanChunk(sentence);
            if (cleaned.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(cleaned);
            if (builder.length() >= MIN_CHUNK_LENGTH) {
                chunks.add(trimToMax(builder.toString()));
                builder.setLength(0);
                if (chunks.size() >= desiredCount) {
                    return;
                }
            }
        }
        String remainder = cleanChunk(builder.toString());
        if (isMeaningful(remainder)) {
            chunks.add(trimToMax(remainder));
        }
    }

    private GeneratedFlashcardCandidate toCandidate(String chunk, String language, String difficulty) {
        String excerpt = excerpt(chunk);
        String questionExcerpt = stripTrailingPunctuation(excerpt);
        return new GeneratedFlashcardCandidate(
                fallbackQuestion(questionExcerpt, language, difficulty),
                chunk,
                GENERATED_EXPLANATION,
                excerpt
        );
    }

    private String fallbackQuestion(String questionExcerpt, String language, String difficulty) {
        boolean vietnamese = isVietnameseLanguage(language);
        String normalizedDifficulty = normalizeOption(difficulty);

        if (vietnamese) {
            if (DIFFICULTY_EASY.equals(normalizedDifficulty)) {
                return "Ý chính của nội dung này là gì?";
            }
            if (DIFFICULTY_HARD.equals(normalizedDifficulty)) {
                return "Có thể giải thích hoặc áp dụng ý này như thế nào: " + questionExcerpt + "?";
            }
            return "Ý chính của đoạn này là gì: " + questionExcerpt + "?";
        }

        if (DIFFICULTY_EASY.equals(normalizedDifficulty)) {
            return "What is the main idea of this content?";
        }
        if (DIFFICULTY_HARD.equals(normalizedDifficulty)) {
            return "How would you explain or apply this idea: " + questionExcerpt + "?";
        }
        return "What is the key idea of: " + questionExcerpt + "?";
    }

    private String normalizeOption(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private boolean isVietnameseLanguage(String value) {
        return VIETNAMESE_LANGUAGE_OPTIONS.contains(normalizeOption(value));
    }

    private String cleanText(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.replace("\r\n", "\n").replace('\r', '\n').trim();
        String[] paragraphs = normalized.split("\\n\\s*\\n");
        List<String> cleanedParagraphs = new ArrayList<>();
        for (String paragraph : paragraphs) {
            String cleaned = cleanChunk(paragraph);
            if (!cleaned.isBlank()) {
                cleanedParagraphs.add(cleaned);
            }
        }
        return String.join("\n\n", cleanedParagraphs);
    }

    private String cleanChunk(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private boolean isMeaningful(String value) {
        return value != null && value.length() >= MIN_CHUNK_LENGTH;
    }

    private String trimToMax(String value) {
        if (value.length() <= MAX_CHUNK_LENGTH) {
            return value;
        }
        int end = value.lastIndexOf(' ', MAX_CHUNK_LENGTH);
        if (end < MIN_CHUNK_LENGTH) {
            end = MAX_CHUNK_LENGTH;
        }
        return value.substring(0, end).trim();
    }

    private String excerpt(String value) {
        if (value.length() <= EXCERPT_MAX_LENGTH) {
            return value;
        }
        int end = value.lastIndexOf(' ', EXCERPT_MAX_LENGTH - 3);
        if (end < MIN_CHUNK_LENGTH) {
            end = EXCERPT_MAX_LENGTH - 3;
        }
        return value.substring(0, end).trim() + "...";
    }

    private String stripTrailingPunctuation(String value) {
        return value.replaceAll("[.!?]+$", "").trim();
    }

    private enum StructuredField {
        FRONT,
        BACK,
        EXPLANATION
    }

    private record ParsedLabel(StructuredField field, String value) {
    }

    private static final class StructuredCardBuilder {
        private final StringBuilder front = new StringBuilder();
        private final StringBuilder back = new StringBuilder();
        private final StringBuilder explanation = new StringBuilder();

        private boolean hasFrontAndBack() {
            return !front.isEmpty() && !back.isEmpty();
        }
    }
}
