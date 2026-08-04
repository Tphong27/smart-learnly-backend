package com.smartlearnly.backend.course.catalog.dto;

import java.util.UUID;

public record LearningObjectiveResponse(
		UUID id,
		String code,
		String description) {
}
