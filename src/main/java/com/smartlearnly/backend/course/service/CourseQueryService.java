package com.smartlearnly.backend.course.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.math.BigDecimal;

import com.smartlearnly.backend.course.dto.CategorySummaryResponse;
import com.smartlearnly.backend.course.dto.CourseCatalogSort;
import com.smartlearnly.backend.course.dto.CourseDetailResponse;
import com.smartlearnly.backend.course.dto.CourseListItemResponse;
import com.smartlearnly.backend.course.dto.LearningObjectiveResponse;
import com.smartlearnly.backend.course.dto.LessonPreviewResponse;
import com.smartlearnly.backend.course.dto.ModulePreviewResponse;
import com.smartlearnly.backend.course.repository.CategoryRepository;
import com.smartlearnly.backend.course.repository.CourseDetailProjection;
import com.smartlearnly.backend.course.repository.CourseListProjection;
import com.smartlearnly.backend.course.repository.CourseRepository;
import com.smartlearnly.backend.curriculum.entity.CurriculumLesson;
import com.smartlearnly.backend.curriculum.entity.CurriculumSection;
import com.smartlearnly.backend.curriculum.entity.CurriculumVersion;
import com.smartlearnly.backend.curriculum.service.CurriculumResolution;
import com.smartlearnly.backend.curriculum.service.CurriculumResolutionService;
import com.smartlearnly.backend.learning.lesson.entity.LessonStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.smartlearnly.backend.classroom.repository.ClassOfferingRepository;
import com.smartlearnly.backend.classroom.repository.CoursePublicProjection;
import com.smartlearnly.backend.course.dto.CourseClassResponse;

@Service
@Transactional(readOnly = true)
public class CourseQueryService {

	private static final int MAX_PAGE_SIZE = 100;
	private static final Set<String> RESERVED_COURSE_SLUGS = Set.of("search", "category");

	private final CourseRepository courseRepository;
	private final CategoryRepository categoryRepository;
	private final ClassOfferingRepository classOfferingRepository;
	private final CurriculumResolutionService curriculumResolutionService;

	public CourseQueryService(
			CourseRepository courseRepository,
			CategoryRepository categoryRepository,
			ClassOfferingRepository classOfferingRepository,
			CurriculumResolutionService curriculumResolutionService) {
		this.courseRepository = courseRepository;
		this.categoryRepository = categoryRepository;
		this.classOfferingRepository = classOfferingRepository;
		this.curriculumResolutionService = curriculumResolutionService;
	}

	public Page<CourseListItemResponse> getCourses(int page, int size) {
		return courseRepository.findPublishedCourses(pageRequest(page, size))
				.map(this::toResponse);
	}

	public Page<CourseListItemResponse> getCourses(
			String keyword,
			String categorySlug,
			BigDecimal minPrice,
			BigDecimal maxPrice,
			boolean onSale,
			Boolean featured,
			CourseCatalogSort sort,
			int page,
			int size) {
		if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
			throw new ResponseStatusException(
					HttpStatus.BAD_REQUEST,
					"Minimum price cannot exceed maximum price");
		}

		CourseCatalogSort resolvedSort = sort == null ? CourseCatalogSort.POPULAR : sort;
		return courseRepository.findPublishedCoursesByFilters(
				toSearchPattern(keyword),
				normalizeOptional(categorySlug),
				minPrice,
				maxPrice,
				onSale,
				featured,
				resolvedSort.name(),
				pageRequest(page, size))
				.map(this::toResponse);
	}

	public Page<CourseListItemResponse> searchCourses(String keyword, int page, int size) {
		String searchPattern = toSearchPattern(keyword);
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

	public CourseDetailResponse getCourseDetail(String slugOrId) {
		if (RESERVED_COURSE_SLUGS.contains(slugOrId)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found");
		}

		// Chấp nhận cả UUID lẫn slug để mọi nơi điều hướng tới /courses/{idOrSlug} đều resolve được.
		CourseDetailProjection course = resolveCourse(slugOrId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found"));
		List<LearningObjectiveResponse> objectives = courseRepository.findLearningObjectivesByCourseId(course.getId())
				.stream()
				.map(objective -> new LearningObjectiveResponse(
						objective.getId(),
						objective.getCode(),
						objective.getDescription()))
				.toList();
		CurriculumResolution curriculum = curriculumResolutionService.resolvePublicMaster(course.getId());
		List<ModulePreviewResponse> modules = toModules(curriculum.version());
		List<CourseClassResponse> classes = classOfferingRepository
		        .findPublicClassesByCourseId(course.getId())
		        .stream()
		        .map(this::toCourseClassResponse)
		        .toList();

		return new CourseDetailResponse(
		        course.getId(),
		        course.getTitle(),
		        course.getSlug(),
		        course.getDescription(),
		        course.getPrice(),
		        course.getDiscountedPrice(),
		        course.getAvatarUrl(),
		        course.isFeatured(),
		        new CategorySummaryResponse(
		                course.getCategoryId(),
		                course.getCategoryName(),
		                course.getCategorySlug()),
		        objectives,
		        modules,
		        classes);
	}

	private java.util.Optional<CourseDetailProjection> resolveCourse(String slugOrId) {
		UUID courseId = tryParseUuid(slugOrId);
		if (courseId != null) {
			return courseRepository.findPublishedCourseById(courseId);
		}
		return courseRepository.findPublishedCourseBySlug(slugOrId);
	}

	private UUID tryParseUuid(String value) {
		try {
			return UUID.fromString(value);
		} catch (IllegalArgumentException ex) {
			return null;
		}
	}

	private CourseClassResponse toCourseClassResponse(CoursePublicProjection classOffering) {
		long activeCount = classOffering.getActiveEnrollmentCount() == null
				? 0
				: classOffering.getActiveEnrollmentCount();

		long maxStudents = classOffering.getMaxStudents() == null
				? 0
				: classOffering.getMaxStudents();

		return new CourseClassResponse(
				classOffering.getId(),
				classOffering.getCourseId(),
				classOffering.getClassName(),
				classOffering.getTrainerId(),
				classOffering.getTrainerName(),
				classOffering.getScheduleDescription(),
				classOffering.getStartDate(),
				classOffering.getEndDate(),
				classOffering.getMaxStudents(),
				activeCount,
				Math.max(0, maxStudents - activeCount),
				classOffering.getStatus());
	}

	private List<ModulePreviewResponse> toModules(CurriculumVersion version) {
		return orderedSections(version).stream()
				.map(section -> new ModulePreviewResponse(
						section.getId(),
						section.getTitle(),
						safeOrder(section.getSortOrder()),
						toLessonPreviews(section)))
				.toList();
	}

	private List<LessonPreviewResponse> toLessonPreviews(CurriculumSection section) {
		return orderedLessons(section).stream()
				.filter(lesson -> lesson.getStatus() == LessonStatus.PUBLISHED)
				.map(lesson -> new LessonPreviewResponse(
						lesson.getId(),
						lesson.getTitle(),
						lesson.getType() == null ? null : lesson.getType().name(),
						safeOrder(lesson.getSortOrder()),
						Boolean.TRUE.equals(lesson.getPreview())))
				.toList();
	}

	private List<CurriculumSection> orderedSections(CurriculumVersion version) {
		return version.getSections().stream()
				.sorted(Comparator
						.comparing(CurriculumSection::getSortOrder, Comparator.nullsLast(Integer::compareTo))
						.thenComparing(CurriculumSection::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
						.thenComparing(CurriculumSection::getId, Comparator.nullsLast(UUID::compareTo)))
				.toList();
	}

	private List<CurriculumLesson> orderedLessons(CurriculumSection section) {
		return section.getLessons().stream()
				.sorted(Comparator
						.comparing(CurriculumLesson::getSortOrder, Comparator.nullsLast(Integer::compareTo))
						.thenComparing(CurriculumLesson::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
						.thenComparing(CurriculumLesson::getId, Comparator.nullsLast(UUID::compareTo)))
				.toList();
	}

	private int safeOrder(Integer sortOrder) {
		return sortOrder == null ? 0 : sortOrder;
	}

	private String toSearchPattern(String keyword) {
		String normalizedKeyword = normalizeOptional(keyword);
		return normalizedKeyword == null
				? null
				: "%" + escapeLikePattern(normalizedKeyword) + "%";
	}

	private String normalizeOptional(String value) {
		if (value == null) {
			return null;
		}

		String normalizedValue = value.trim();
		return normalizedValue.isEmpty() ? null : normalizedValue;
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
				course.getDiscountedPrice(),
				course.getAvatarUrl(),
				course.isFeatured(),
				category);
	}
}
