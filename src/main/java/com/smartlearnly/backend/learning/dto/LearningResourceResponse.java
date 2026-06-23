package com.smartlearnly.backend.learning.dto;

public record LearningResourceResponse(
    String url,
    String name,
    String contentType
) {
}
