package com.smartlearnly.backend.course.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.smartlearnly.backend.course.dto.CategorySummaryResponse;
import com.smartlearnly.backend.course.dto.CourseDetailResponse;
import com.smartlearnly.backend.course.dto.CourseListItemResponse;
import com.smartlearnly.backend.course.dto.LearningObjectiveResponse;
import com.smartlearnly.backend.course.dto.LessonPreviewResponse;
import com.smartlearnly.backend.course.dto.ModulePreviewResponse;
import com.smartlearnly.backend.course.repository.CategoryRepository;
import com.smartlearnly.backend.course.repository.CourseDetailProjection;
import com.smartlearnly.backend.course.repository.CourseListProjection;
import com.smartlearnly.backend.course.repository.CourseRepository;
import com.smartlearnly.backend.course.repository.CurriculumRowProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class CourseQueryService {

	private static final int MAX_PAGE_SIZE = 100;
	private static final Set<String> RESERVED_COURSE_SLUGS = Set.of("search", "category");

	private final CourseRepository courseRepository;
	private final CategoryRepository categoryRepository;

	public CourseQueryService(
			CourseRepository courseRepository,
			CategoryRepository categoryRepository) {
		this.courseRepository = courseRepository;
		this.categoryRepository = categoryRepository;
	}

	public Page<CourseListItemResponse> getCourses(int page, int size) {
		return courseRepository.findPublishedCourses(pageRequest(page, size))
				.map(this::toResponse);
	}

	public Page<CourseListItemResponse> searchCourses(String keyword, int page, int size) {
		String searchPattern = "%" + escapeLikePattern(keyword.trim()) + "%";
		return courseRepository.searchPublishedCourses(searchPattern, pageRequest(page, size))
				.map(this::toResponse);
	}

	public Page<CourseListItemResponse> getCoursesByCategory(
			String categorySlug,
			int page,
			int size) {
		if (!categoryRepository.existsBySlug(categorySlug)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found");
		}

		return courseRepository.findPublishedCoursesByCategorySlug(
					categorySlug,
					pageRequest(page, size))
				.map(this::toResponse);
	}

	public CourseDetailResponse getCourseDetail(String slug) {
		if (RESERVED_COURSE_SLUGS.contains(slug)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found");
		}

		CourseDetailProjection course = courseRepository.findPublishedCourseBySlug(slug)
				.orElseThrow(() ->
						new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found"));
		List<LearningObjectiveResponse> objectives =
				courseRepository.findLearningObjectivesByCourseId(course.getId())
						.stream()
						.map(objective -> new LearningObjectiveResponse(
								objective.getId(),
								objective.getCode(),
								objective.getDescription()))
						.toList();
		List<ModulePreviewResponse> modules = toModules(
				courseRepository.findActiveCurriculumByCourseId(course.getId()));

		return new CourseDetailResponse(
				course.getId(),
				course.getTitle(),
				course.getSlug(),
				course.getDescription(),
				course.getPrice(),
				course.getAvatarUrl(),
				course.isFeatured(),
				new CategorySummaryResponse(
						course.getCategoryId(),
						course.getCategoryName(),
						course.getCategorySlug()),
				objectives,
				modules);
	}

	private List<ModulePreviewResponse> toModules(List<CurriculumRowProjection> rows) {
		Map<UUID, ModuleAccumulator> modules = new LinkedHashMap<>();

		for (CurriculumRowProjection row : rows) {
			ModuleAccumulator module = modules.computeIfAbsent(
					row.getModuleId(),
					id -> new ModuleAccumulator(
							id,
							row.getModuleTitle(),
							row.getModuleOrderIndex()));

			if (row.getLessonId() != null) {
				module.lessons().add(new LessonPreviewResponse(
						row.getLessonId(),
						row.getLessonTitle(),
						row.getLessonType(),
						row.getLessonOrderIndex(),
						row.getLessonPreview()));
			}
		}

		return modules.values()
				.stream()
				.map(module -> new ModulePreviewResponse(
						module.id(),
						module.title(),
						module.orderIndex(),
						module.lessons()))
				.toList();
	}

	private PageRequest pageRequest(int page, int size) {
		return PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE));
	}

	private String escapeLikePattern(String value) {
		return value.replace("\\", "\\\\")
				.replace("%", "\\%")
				.replace("_", "\\_");
	}

	private CourseListItemResponse toResponse(CourseListProjection course) {
		CategorySummaryResponse category = new CategorySummaryResponse(
				course.getCategoryId(),
				course.getCategoryName(),
				course.getCategorySlug());

		return new CourseListItemResponse(
				course.getId(),
				course.getTitle(),
				course.getSlug(),
				course.getDescription(),
				course.getPrice(),
				course.getAvatarUrl(),
				course.isFeatured(),
				category);
	}

	private record ModuleAccumulator(
			UUID id,
			String title,
			int orderIndex,
			List<LessonPreviewResponse> lessons) {

		private ModuleAccumulator(UUID id, String title, int orderIndex) {
			this(id, title, orderIndex, new ArrayList<>());
		}
	}
}
