package com.smartlearnly.backend.course.catalog.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.math.BigDecimal;

import com.smartlearnly.backend.common.api.PageResponse;
import com.smartlearnly.backend.course.dto.CategorySummaryResponse;
import com.smartlearnly.backend.course.catalog.dto.CourseCatalogSort;
import com.smartlearnly.backend.course.catalog.dto.CourseClassResponse;
import com.smartlearnly.backend.course.catalog.dto.CourseDetailResponse;
import com.smartlearnly.backend.course.catalog.dto.CourseListItemResponse;
import com.smartlearnly.backend.course.catalog.dto.LearningObjectiveResponse;
import com.smartlearnly.backend.course.catalog.dto.LessonPreviewResponse;
import com.smartlearnly.backend.course.catalog.dto.ModulePreviewResponse;
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

@Service
@Transactional(readOnly = true)
public class CourseQueryService {

	private static final int MAX_PAGE_SIZE = 100;
	private static final Set<String> RESERVED_COURSE_SLUGS = Set.of("search", "category");

	private final CourseRepository courseRepository;
	private final CategoryRepository categoryRepository;
	private final ClassOfferingRepository classOfferingRepository;
	private final CurriculumResolutionService curriculumResolutionService;

	// Khởi tạo service catalog với các nguồn dữ liệu course, category, class và curriculum.
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

	// Tải trang khóa học published theo phân trang mặc định.
	public PageResponse<CourseListItemResponse> getCourses(int page, int size) {
		return toPageResponse(courseRepository.findPublishedCourses(pageRequest(page, size))
				.map(this::toResponse));
	}

	// Tải catalog published theo từ khóa, category, giá, sale, featured và sort.
	public PageResponse<CourseListItemResponse> getCourses(
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
		return toPageResponse(courseRepository.findPublishedCoursesByFilters(
				toSearchPattern(keyword),
				normalizeOptional(categorySlug),
				minPrice,
				maxPrice,
				onSale,
				featured,
				resolvedSort.name(),
				pageRequest(page, size))
				.map(this::toResponse));
	}

	// Tìm course published bằng pattern LIKE đã escape.
	public PageResponse<CourseListItemResponse> searchCourses(String keyword, int page, int size) {
		String searchPattern = toSearchPattern(keyword);
		return toPageResponse(courseRepository.searchPublishedCourses(searchPattern, pageRequest(page, size))
				.map(this::toResponse));
	}

	// Tải course published trong category còn hoạt động.
	public PageResponse<CourseListItemResponse> getCoursesByCategory(
			String categorySlug,
			int page,
			int size) {
		if (!categoryRepository.existsBySlug(categorySlug)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found");
		}

		return toPageResponse(courseRepository.findPublishedCoursesByCategorySlug(
				categorySlug,
				pageRequest(page, size))
				.map(this::toResponse));
	}

	// Tổng hợp course detail, mục tiêu, curriculum preview và lớp đang mở.
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

	// Tìm course published theo UUID nếu parse được, nếu không dùng slug.
	private java.util.Optional<CourseDetailProjection> resolveCourse(String slugOrId) {
		UUID courseId = tryParseUuid(slugOrId);
		if (courseId != null) {
			return courseRepository.findPublishedCourseById(courseId);
		}
		return courseRepository.findPublishedCourseBySlug(slugOrId);
	}

	// Parse UUID an toàn và trả null khi đầu vào là slug.
	private UUID tryParseUuid(String value) {
		try {
			return UUID.fromString(value);
		} catch (IllegalArgumentException ex) {
			return null;
		}
	}

	// Chuyển projection lớp đang mở thành DTO hiển thị trong course detail.
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

	// Chuyển curriculum version thành danh sách module preview đã sắp xếp.
	private List<ModulePreviewResponse> toModules(CurriculumVersion version) {
		return orderedSections(version).stream()
				.map(section -> new ModulePreviewResponse(
						section.getId(),
						section.getTitle(),
						safeOrder(section.getSortOrder()),
						toLessonPreviews(section)))
				.toList();
	}

	// Chuyển lesson published trong section thành preview an toàn.
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

	// Sắp xếp section ổn định theo sortOrder rồi ID.
	private List<CurriculumSection> orderedSections(CurriculumVersion version) {
		return version.getSections().stream()
				.sorted(Comparator
						.comparing(CurriculumSection::getSortOrder, Comparator.nullsLast(Integer::compareTo))
						.thenComparing(CurriculumSection::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
						.thenComparing(CurriculumSection::getId, Comparator.nullsLast(UUID::compareTo)))
				.toList();
	}

	// Sắp xếp lesson ổn định theo sortOrder rồi ID.
	private List<CurriculumLesson> orderedLessons(CurriculumSection section) {
		return section.getLessons().stream()
				.sorted(Comparator
						.comparing(CurriculumLesson::getSortOrder, Comparator.nullsLast(Integer::compareTo))
						.thenComparing(CurriculumLesson::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
						.thenComparing(CurriculumLesson::getId, Comparator.nullsLast(UUID::compareTo)))
				.toList();
	}

	// Chuyển sortOrder null thành giá trị cuối danh sách.
	private int safeOrder(Integer sortOrder) {
		return sortOrder == null ? 0 : sortOrder;
	}

	// Chuẩn hóa keyword và tạo pattern LIKE không phân biệt hoa thường.
	private String toSearchPattern(String keyword) {
		String normalizedKeyword = normalizeOptional(keyword);
		return normalizedKeyword == null
				? null
				: "%" + escapeLikePattern(normalizedKeyword) + "%";
	}

	// Trim chuỗi tùy chọn và đổi chuỗi rỗng thành null.
	private String normalizeOptional(String value) {
		if (value == null) {
			return null;
		}

		String normalizedValue = value.trim();
		return normalizedValue.isEmpty() ? null : normalizedValue;
	}

	// Giới hạn page size để bảo vệ truy vấn catalog công khai.
	private PageRequest pageRequest(int page, int size) {
		return PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE));
	}

	// Chuyển Page nội bộ của Spring Data thành contract phân trang ổn định cho API catalog.
	private PageResponse<CourseListItemResponse> toPageResponse(Page<CourseListItemResponse> page) {
		return new PageResponse<>(
				page.getContent(),
				page.getNumber(),
				page.getSize(),
				page.getTotalElements(),
				page.getTotalPages());
	}

	// Escape ký tự đặc biệt của SQL LIKE trước khi tìm kiếm.
	private String escapeLikePattern(String value) {
		return value.replace("\\", "\\\\")
				.replace("%", "\\%")
				.replace("_", "\\_");
	}

	// Chuyển projection course thành item catalog công khai.
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
