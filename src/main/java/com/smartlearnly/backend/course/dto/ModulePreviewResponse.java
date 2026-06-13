package com.smartlearnly.backend.course.dto;

import java.util.List;
import java.util.UUID;

public record ModulePreviewResponse(
		UUID id,
		String title,
		int orderIndex,
		List<LessonPreviewResponse> lessons) {

	public ModulePreviewResponse {
		lessons = List.copyOf(lessons);
	}
}
