package com.smartlearnly.backend.classroom.dto;

public record StudentPerformanceQuery(
        String keyword,
        String progress,
        String indicator,
        int page,
        int size
) {
}