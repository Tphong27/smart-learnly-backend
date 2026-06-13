package com.smartlearnly.backend.course.dto;

import java.util.UUID;

public record CategorySummaryResponse(
		UUID id,
		String name,
		String slug) {
}
