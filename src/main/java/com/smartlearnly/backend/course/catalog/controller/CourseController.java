package com.smartlearnly.backend.course.catalog.controller;

import com.smartlearnly.backend.common.api.PageResponse;
import com.smartlearnly.backend.course.catalog.dto.CourseCatalogSort;
import com.smartlearnly.backend.course.catalog.dto.CourseDetailResponse;
import com.smartlearnly.backend.course.catalog.dto.CourseListItemResponse;
import com.smartlearnly.backend.course.catalog.service.CourseQueryService;
import java.math.BigDecimal;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/courses")
public class CourseController {

	private final CourseQueryService courseQueryService;

	// Khởi tạo controller catalog với service truy vấn chỉ đọc.
	public CourseController(CourseQueryService courseQueryService) {
		this.courseQueryService = courseQueryService;
	}

	// Tải catalog khóa học published theo bộ lọc và cách sắp xếp.
	@GetMapping
	public PageResponse<CourseListItemResponse> getCourses(
			@RequestParam(required = false) String keyword,
			@RequestParam(required = false) String categorySlug,
			@RequestParam(required = false) @DecimalMin("0.0") BigDecimal minPrice,
			@RequestParam(required = false) @DecimalMin("0.0") BigDecimal maxPrice,
			@RequestParam(defaultValue = "false") boolean onSale,
			@RequestParam(required = false) Boolean featured,
			@RequestParam(defaultValue = "POPULAR") CourseCatalogSort sort,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) int size) {
		return courseQueryService.getCourses(
				keyword,
				categorySlug,
				minPrice,
				maxPrice,
				onSale,
				featured,
				sort,
				page,
				size);
	}

	// Tìm khóa học published theo từ khóa bắt buộc.
	@GetMapping("/search")
	public PageResponse<CourseListItemResponse> searchCourses(
			@RequestParam @NotBlank String keyword,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) int size) {
		return courseQueryService.searchCourses(keyword, page, size);
	}

	// Tải khóa học published thuộc một category slug.
	@GetMapping("/category/{categorySlug}")
	public PageResponse<CourseListItemResponse> getCoursesByCategory(
			@PathVariable String categorySlug,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) int size) {
		return courseQueryService.getCoursesByCategory(categorySlug, page, size);
	}

	// Trả chi tiết khóa học published theo slug hoặc ID.
	@GetMapping("/{slug}")
	public CourseDetailResponse getCourseDetail(@PathVariable String slug) {
		return courseQueryService.getCourseDetail(slug);
	}
}
