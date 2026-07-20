package com.smartlearnly.backend.assignment.dto;

public class AssignmentAiDraftModel {
    public record Response(
            String content,
            String sourceName,
            Integer sourceCharactersUsed,
            String sourceCacheKey
    ) {
    }
}
