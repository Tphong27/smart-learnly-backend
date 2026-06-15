package com.smartlearnly.backend.course.dto;

import java.util.UUID;

public record LessonPreviewResponse(
		UUID id,
		String title,
		String lessonType,
		int orderIndex,
		boolean preview) {
}
