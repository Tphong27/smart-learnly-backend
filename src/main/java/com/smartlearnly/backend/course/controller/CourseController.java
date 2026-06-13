package com.smartlearnly.backend.course.controller;

import com.smartlearnly.backend.course.dto.CourseDetailResponse;
import com.smartlearnly.backend.course.dto.CourseListItemResponse;
import com.smartlearnly.backend.course.service.CourseQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/courses")
@Tag(name = "Courses", description = "Public course browsing endpoints")
public class CourseController {

	private final CourseQueryService courseQueryService;

	public CourseController(CourseQueryService courseQueryService) {
		this.courseQueryService = courseQueryService;
	}

	@GetMapping
	@Operation(summary = "List published courses")
	public Page<CourseListItemResponse> getCourses(
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) int size) {
		return courseQueryService.getCourses(page, size);
	}

	@GetMapping("/search")
	@Operation(summary = "Search published courses")
	public Page<CourseListItemResponse> searchCourses(
			@RequestParam @NotBlank String keyword,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) int size) {
		return courseQueryService.searchCourses(keyword, page, size);
	}

	@GetMapping("/category/{categorySlug}")
	@Operation(summary = "List published courses by category")
	public Page<CourseListItemResponse> getCoursesByCategory(
			@PathVariable String categorySlug,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) int size) {
		return courseQueryService.getCoursesByCategory(categorySlug, page, size);
	}

	@GetMapping("/{slug}")
	@Operation(summary = "Get published course detail")
	public CourseDetailResponse getCourseDetail(@PathVariable String slug) {
		return courseQueryService.getCourseDetail(slug);
	}
}
