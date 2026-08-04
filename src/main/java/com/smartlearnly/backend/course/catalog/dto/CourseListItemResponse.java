package com.smartlearnly.backend.course.catalog.dto;

import com.smartlearnly.backend.course.dto.CategorySummaryResponse;

import java.math.BigDecimal;
import java.util.UUID;

public record CourseListItemResponse(
		UUID id,
		String title,
		String slug,
		String description,
		BigDecimal price,
		BigDecimal discountedPrice,
		String avatarUrl,
		boolean featured,
		CategorySummaryResponse category) {
}
