package com.smartlearnly.backend.course.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CourseListItemResponse(
		UUID id,
		String title,
		String slug,
		String description,
		BigDecimal price,
		String avatarUrl,
		boolean featured,
		CategorySummaryResponse category) {
}
