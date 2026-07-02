package com.smartlearnly.backend.flashcard.staging.service;

import com.smartlearnly.backend.flashcard.staging.service.FlashcardTextGenerationService.GeneratedFlashcardCandidate;
import com.smartlearnly.backend.flashcard.staging.service.FlashcardTextGenerationService.GenerationRequest;
import com.smartlearnly.backend.flashcard.staging.service.FlashcardTextGenerationService.GenerationResult;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class RuleBasedFlashcardTextGenerationService implements FlashcardTextGenerationService {
    private static final String SOURCE_TYPE_TEXT = "TEXT";
    private static final String GENERATED_EXPLANATION = "Generated from pasted text.";
    private static final int MIN_CHUNK_LENGTH = 60;
    private static final int MAX_CHUNK_LENGTH = 700;
    private static final int EXCERPT_MAX_LENGTH = 140;

    @Override
    public GenerationResult generate(GenerationRequest request) {
        String sourceText = cleanText(request.sourceText());
        List<String> chunks = chunks(sourceText, request.desiredCount());
        List<GeneratedFlashcardCandidate> candidates = chunks.stream()
                .limit(request.desiredCount())
                .map(this::toCandidate)
                .toList();
        return new GenerationResult(SOURCE_TYPE_TEXT, candidates);
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

    private GeneratedFlashcardCandidate toCandidate(String chunk) {
        String excerpt = excerpt(chunk);
        String questionExcerpt = stripTrailingPunctuation(excerpt);
        return new GeneratedFlashcardCandidate(
                "What is the key idea of: " + questionExcerpt + "?",
                chunk,
                GENERATED_EXPLANATION,
                excerpt
        );
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
}
