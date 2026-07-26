package com.smartlearnly.backend.question.ai.generation;

import com.smartlearnly.backend.question.ai.dto.AiQuestionDraftDtos;
import java.util.List;
import java.util.UUID;

public interface QuestionGenerationProvider {
    GenerationResult generate(GenerationRequest request);

    String providerName();

    String modelName();

    record GenerationRequest(
            UUID batchId,
            int requestedCount,
            List<String> questionTypes,
            String language,
            String generationInstruction,
            List<SourceInput> sources
    ) {
    }

    record SourceInput(
            UUID generationSourceId,
            String sourceName,
            String checksum,
            String version,
            List<ChunkInput> chunks
    ) {
    }

    record ChunkInput(
            UUID chunkId,
            String chunkReference,
            String excerpt
    ) {
    }

    record GenerationResult(
            List<GeneratedQuestion> questions,
            Integer promptTokens,
            Integer completionTokens,
            Integer totalTokens
    ) {
    }

    record GeneratedQuestion(
            String questionText,
            String questionType,
            List<AiQuestionDraftDtos.AnswerPayload> answers,
            String explanation,
            List<GeneratedEvidence> evidence
    ) {
    }

    record GeneratedEvidence(
            UUID generationSourceId,
            UUID chunkId,
            String chunkReference,
            String excerpt,
            boolean supportsCorrectAnswer
    ) {
    }
}
