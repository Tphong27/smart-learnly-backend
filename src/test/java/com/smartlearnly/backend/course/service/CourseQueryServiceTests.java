package com.smartlearnly.backend.course.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
import com.smartlearnly.backend.course.repository.CurriculumRowProjection;
import com.smartlearnly.backend.course.repository.LearningObjectiveProjection;
import com.smartlearnly.backend.curriculum.entity.CurriculumLesson;
import com.smartlearnly.backend.curriculum.entity.CurriculumScope;
import com.smartlearnly.backend.curriculum.entity.CurriculumSection;
import com.smartlearnly.backend.curriculum.entity.CurriculumStatus;
import com.smartlearnly.backend.curriculum.entity.CurriculumVersion;
import com.smartlearnly.backend.curriculum.service.CurriculumResolution;
import com.smartlearnly.backend.curriculum.service.CurriculumResolutionService;
import com.smartlearnly.backend.learning.lesson.entity.LessonStatus;
import com.smartlearnly.backend.learning.lesson.entity.LessonType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.web.server.ResponseStatusException;
import com.smartlearnly.backend.classroom.repository.ClassOfferingRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseQueryServiceTests {

	@Mock
	private CourseRepository courseRepository;

	@Mock
	private CategoryRepository categoryRepository;

	@Mock
	private ClassOfferingRepository classOfferingRepository;

	@Mock
	private CurriculumResolutionService curriculumResolutionService;

	@Mock
	private CourseListProjection projection;

	@Mock
	private CourseDetailProjection detailProjection;

	@Mock
	private LearningObjectiveProjection objectiveProjection;

	private CourseQueryService service() {
		return new CourseQueryService(courseRepository, categoryRepository, classOfferingRepository,
				curriculumResolutionService);
	}

	@Test
	void mapsPublishedCourseProjectionToResponse() {
		UUID courseId = UUID.randomUUID();
		UUID categoryId = UUID.randomUUID();
		PageRequest pageable = PageRequest.of(0, 20);

		when(projection.getId()).thenReturn(courseId);
		when(projection.getTitle()).thenReturn("Spring Boot Fundamentals");
		when(projection.getSlug()).thenReturn("spring-boot-fundamentals");
		when(projection.getDescription()).thenReturn("Build production-ready Spring applications.");
		when(projection.getPrice()).thenReturn(new BigDecimal("49.90"));
		when(projection.getDiscountedPrice()).thenReturn(new BigDecimal("39.90"));
		when(projection.getAvatarUrl()).thenReturn("https://example.test/course.png");
		when(projection.isFeatured()).thenReturn(true);
		when(projection.getCategoryId()).thenReturn(categoryId);
		when(projection.getCategoryName()).thenReturn("Backend Development");
		when(projection.getCategorySlug()).thenReturn("backend-development");
		when(courseRepository.findPublishedCourses(pageable))
				.thenReturn(new PageImpl<>(List.of(projection), pageable, 1));

		Page<CourseListItemResponse> result = service().getCourses(0, 20);

		assertThat(result.getTotalElements()).isEqualTo(1);
		assertThat(result.getContent().get(0))
				.isEqualTo(new CourseListItemResponse(
						courseId,
						"Spring Boot Fundamentals",
						"spring-boot-fundamentals",
						"Build production-ready Spring applications.",
						new BigDecimal("49.90"),
						new BigDecimal("39.90"),
						"https://example.test/course.png",
						true,
						new CategorySummaryResponse(
								categoryId,
								"Backend Development",
								"backend-development")));
		verify(courseRepository).findPublishedCourses(pageable);
	}

	@Test
	void capsRequestedPageSizeAtOneHundred() {
		PageRequest cappedPageable = PageRequest.of(2, 100);
		when(courseRepository.findPublishedCourses(cappedPageable))
				.thenReturn(Page.empty(cappedPageable));

		service().getCourses(2, 250);

		verify(courseRepository).findPublishedCourses(cappedPageable);
	}

	@Test
	void filtersPublishedCoursesWithNormalizedParamsAndSelectedSort() {
		PageRequest pageable = PageRequest.of(0, 20);
		when(courseRepository.findPublishedCoursesByFilters(
				"%java%",
				"programming",
				new BigDecimal("10000"),
				new BigDecimal("500000"),
				true,
				true,
				"PRICE_ASC",
				pageable))
				.thenReturn(Page.empty(pageable));

		Page<CourseListItemResponse> result = service().getCourses(
				" java ",
				" programming ",
				new BigDecimal("10000"),
				new BigDecimal("500000"),
				true,
				true,
				CourseCatalogSort.PRICE_ASC,
				0,
				20);

		assertThat(result).isEmpty();
		verify(courseRepository).findPublishedCoursesByFilters(
				"%java%",
				"programming",
				new BigDecimal("10000"),
				new BigDecimal("500000"),
				true,
				true,
				"PRICE_ASC",
				pageable);
	}

	@Test
	void rejectsPriceRangeWhenMinimumExceedsMaximum() {
		assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> service().getCourses(
				null,
				null,
				new BigDecimal("500000"),
				new BigDecimal("100000"),
				false,
				null,
				CourseCatalogSort.POPULAR,
				0,
				20)))
				.isInstanceOf(ResponseStatusException.class)
				.satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value())
						.isEqualTo(400));

		verifyNoInteractions(courseRepository);
	}

	@Test
	void searchesWithTrimmedLiteralPatternAndMapsProjection() {
		PageRequest cappedPageable = PageRequest.of(1, 100);
		when(courseRepository.searchPublishedCourses(
				"%50\\%\\_off\\\\today%",
				cappedPageable))
				.thenReturn(new PageImpl<>(List.of(projection), cappedPageable, 1));

		Page<CourseListItemResponse> result = service()
				.searchCourses("  50%_off\\today  ", 1, 250);

		assertThat(result.getContent()).hasSize(1);
		verify(courseRepository).searchPublishedCourses(
				"%50\\%\\_off\\\\today%",
				cappedPageable);
	}

	@Test
	void repositoryQueryEnforcesPublicCourseVisibilityAndStableOrdering() throws Exception {
		Query query = CourseRepository.class
				.getMethod("findPublishedCourses", Pageable.class)
				.getAnnotation(Query.class);

		assertThat(query.value())
				.contains("c.status = 'published'::public.course_status")
				.contains("c.deleted_at IS NULL")
				.contains("ORDER BY c.is_featured DESC, c.created_at DESC, c.id ASC");
		assertThat(query.countQuery())
				.contains("c.status = 'published'::public.course_status")
				.contains("c.deleted_at IS NULL");
	}

	@Test
	void filteredCatalogQueryUsesOnlyPublishedCoursesAndEffectiveSalePrice() throws Exception {
		Query query = CourseRepository.class
				.getMethod(
						"findPublishedCoursesByFilters",
						String.class,
						String.class,
						BigDecimal.class,
						BigDecimal.class,
						boolean.class,
						Boolean.class,
						String.class,
						Pageable.class)
				.getAnnotation(Query.class);

		assertThat(query.value())
				.contains("c.status = 'published'::public.course_status")
				.contains("c.deleted_at IS NULL")
				.contains("category.slug = :categorySlug")
				.contains("c.discounted_price < c.price")
				.contains("CASE WHEN :sort = 'PRICE_ASC'")
				.contains("CASE WHEN :sort = 'PRICE_DESC'");
		assertThat(query.countQuery())
				.contains("c.status = 'published'::public.course_status")
				.contains("c.deleted_at IS NULL")
				.contains("c.discounted_price < c.price");
	}

	@Test
	void searchQueryMatchesTitleAndDescriptionWithPublicVisibility() throws Exception {
		Query query = CourseRepository.class
				.getMethod("searchPublishedCourses", String.class, Pageable.class)
				.getAnnotation(Query.class);

		assertThat(query.value())
				.contains("c.title ILIKE :searchPattern ESCAPE '\\'")
				.contains("COALESCE(c.description, '') ILIKE :searchPattern ESCAPE '\\'")
				.contains("c.status = 'published'::public.course_status")
				.contains("c.deleted_at IS NULL")
				.contains("ORDER BY c.is_featured DESC, c.created_at DESC, c.id ASC");
		assertThat(query.countQuery())
				.contains("c.title ILIKE :searchPattern ESCAPE '\\'")
				.contains("COALESCE(c.description, '') ILIKE :searchPattern ESCAPE '\\'")
				.contains("c.status = 'published'::public.course_status")
				.contains("c.deleted_at IS NULL");
	}

	@Test
	void unknownCategoryReturnsNotFoundWithoutQueryingCourses() {
		when(categoryRepository.existsBySlug("Java")).thenReturn(false);

		assertThat(org.assertj.core.api.Assertions
				.catchThrowable(
						() -> service().getCoursesByCategory("Java", 0, 20)))
				.isInstanceOf(ResponseStatusException.class)
				.satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value())
						.isEqualTo(404));

		verify(categoryRepository).existsBySlug("Java");
		verifyNoInteractions(courseRepository);
	}

	@Test
	void existingCategoryWithoutCoursesReturnsEmptyPageAndCapsSize() {
		PageRequest cappedPageable = PageRequest.of(2, 100);
		when(categoryRepository.existsBySlug("java")).thenReturn(true);
		when(courseRepository.findPublishedCoursesByCategorySlug("java", cappedPageable))
				.thenReturn(Page.empty(cappedPageable));

		Page<CourseListItemResponse> result = service()
				.getCoursesByCategory("java", 2, 250);

		assertThat(result).isEmpty();
		verify(categoryRepository).existsBySlug("java");
		verify(courseRepository).findPublishedCoursesByCategorySlug("java", cappedPageable);
	}

	@Test
	void categoryQueryUsesExactSlugAndPublicVisibility() throws Exception {
		Query query = CourseRepository.class
				.getMethod(
						"findPublishedCoursesByCategorySlug",
						String.class,
						Pageable.class)
				.getAnnotation(Query.class);

		assertThat(query.value())
				.contains("category.slug = :categorySlug")
				.doesNotContain("LOWER(")
				.contains("c.status = 'published'::public.course_status")
				.contains("c.deleted_at IS NULL")
				.contains("ORDER BY c.is_featured DESC, c.created_at DESC, c.id ASC");
		assertThat(query.countQuery())
				.contains("category.slug = :categorySlug")
				.doesNotContain("LOWER(")
				.contains("c.status = 'published'::public.course_status")
				.contains("c.deleted_at IS NULL");
	}

	@Test
	void mapsPublishedCourseDetailAndOrderedObjectives() {
		UUID courseId = UUID.randomUUID();
		UUID categoryId = UUID.randomUUID();
		UUID objectiveId = UUID.randomUUID();
		UUID firstModuleId = UUID.randomUUID();
		UUID secondModuleId = UUID.randomUUID();
		UUID firstLessonId = UUID.randomUUID();
		UUID secondLessonId = UUID.randomUUID();
		when(detailProjection.getId()).thenReturn(courseId);
		when(detailProjection.getTitle()).thenReturn("Spring Boot Fundamentals");
		when(detailProjection.getSlug()).thenReturn("spring-boot-fundamentals");
		when(detailProjection.getDescription())
				.thenReturn("Build production-ready Spring applications.");
		when(detailProjection.getPrice()).thenReturn(new BigDecimal("49.90"));
		when(detailProjection.getDiscountedPrice()).thenReturn(new BigDecimal("39.90"));
		when(detailProjection.getAvatarUrl()).thenReturn("https://example.test/course.png");
		when(detailProjection.isFeatured()).thenReturn(true);
		when(detailProjection.getCategoryId()).thenReturn(categoryId);
		when(detailProjection.getCategoryName()).thenReturn("Backend Development");
		when(detailProjection.getCategorySlug()).thenReturn("backend-development");
		when(objectiveProjection.getId()).thenReturn(objectiveId);
		when(objectiveProjection.getCode()).thenReturn("OBJ-01");
		when(objectiveProjection.getDescription()).thenReturn("Build a Spring Boot REST API.");
		when(courseRepository.findPublishedCourseBySlug("spring-boot-fundamentals"))
				.thenReturn(Optional.of(detailProjection));
		when(courseRepository.findLearningObjectivesByCourseId(courseId))
				.thenReturn(List.of(objectiveProjection));

		// Course detail now derives its curriculum preview from the resolved public MASTER version.
		// The "Advanced Topics" module has no PUBLISHED lessons, mirroring the original empty-module case.
		CurriculumVersion version = version(courseId);
		CurriculumSection firstModule = section(version, firstModuleId, "Getting Started", 0);
		firstModule.getLessons().add(publishedLesson(firstModule, firstLessonId, "Introduction", LessonType.VIDEO, 0, true));
		firstModule.getLessons().add(publishedLesson(firstModule, secondLessonId, "Project Setup", null, 1, false));
		CurriculumSection secondModule = section(version, secondModuleId, "Advanced Topics", 1);
		version.getSections().add(firstModule);
		version.getSections().add(secondModule);
		when(curriculumResolutionService.resolvePublicMaster(courseId))
				.thenReturn(new CurriculumResolution(version, null, null, false,
						CurriculumResolutionService.SOURCE_MASTER_PUBLIC));

		CourseDetailResponse result = service().getCourseDetail("spring-boot-fundamentals");

		assertThat(result).isEqualTo(new CourseDetailResponse(
				courseId,
				"Spring Boot Fundamentals",
				"spring-boot-fundamentals",
				"Build production-ready Spring applications.",
				new BigDecimal("49.90"),
				new BigDecimal("39.90"),
				"https://example.test/course.png",
				true,
				new CategorySummaryResponse(
						categoryId,
						"Backend Development",
						"backend-development"),
				List.of(new LearningObjectiveResponse(
						objectiveId,
						"OBJ-01",
						"Build a Spring Boot REST API.")),
				List.of(
						new ModulePreviewResponse(
								firstModuleId,
								"Getting Started",
								0,
								List.of(
										new LessonPreviewResponse(
												firstLessonId,
												"Introduction",
												"VIDEO",
												0,
												true),
										new LessonPreviewResponse(
												secondLessonId,
												"Project Setup",
												null,
												1,
												false))),
						new ModulePreviewResponse(
								secondModuleId,
								"Advanced Topics",
								1,
								List.of())),
				List.of()));
		verify(courseRepository).findPublishedCourseBySlug("spring-boot-fundamentals");
		verify(courseRepository).findLearningObjectivesByCourseId(courseId);
		verify(curriculumResolutionService).resolvePublicMaster(courseId);
		verify(classOfferingRepository).findPublicClassesByCourseId(courseId);
	}

	@Test
	void missingPublishedCourseReturnsNotFoundWithoutLoadingObjectives() {
		when(courseRepository.findPublishedCourseBySlug("missing")).thenReturn(Optional.empty());

		assertThat(org.assertj.core.api.Assertions
				.catchThrowable(
						() -> service().getCourseDetail("missing")))
				.isInstanceOf(ResponseStatusException.class)
				.satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value())
						.isEqualTo(404));

		verify(courseRepository).findPublishedCourseBySlug("missing");
	}

	@Test
	void reservedCourseSlugsReturnNotFoundWithoutRepositoryAccess() {
		CourseQueryService service = service();

		assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> service.getCourseDetail("search")))
				.isInstanceOf(ResponseStatusException.class);
		assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> service.getCourseDetail("category")))
				.isInstanceOf(ResponseStatusException.class);

		verifyNoInteractions(courseRepository, categoryRepository);
	}

	@Test
	void detailAndObjectiveQueriesEnforcePublicVisibilityAndOrdering() throws Exception {
		Query detailQuery = CourseRepository.class
				.getMethod("findPublishedCourseBySlug", String.class)
				.getAnnotation(Query.class);
		Query objectiveQuery = CourseRepository.class
				.getMethod("findLearningObjectivesByCourseId", UUID.class)
				.getAnnotation(Query.class);
		Query curriculumQuery = CourseRepository.class
				.getMethod("findActiveCurriculumByCourseId", UUID.class)
				.getAnnotation(Query.class);

		assertThat(detailQuery.value())
				.contains("c.slug = :slug")
				.contains("c.status = 'published'::public.course_status")
				.contains("c.deleted_at IS NULL");
		assertThat(objectiveQuery.value())
				.contains("clo.course_id = :courseId")
				.contains("ORDER BY clo.code ASC, clo.id ASC");
		assertThat(curriculumQuery.value())
				.contains("LEFT JOIN public.lessons l")
				.contains("ON l.module_id = m.id")
				.contains("AND l.status = 'published'::public.lesson_status")
				.contains("m.course_id = :courseId")
				.contains("m.status = 'active'")
				.contains("l.lesson_type::text AS \"lessonType\"")
				.contains(
						"m.order_index ASC",
						"m.id ASC",
						"l.order_index ASC",
						"l.id ASC")
				.doesNotContain("l.content")
				.doesNotContain("materials");
	}

	private CurriculumVersion version(UUID courseId) {
		CurriculumVersion version = new CurriculumVersion();
		version.setId(UUID.randomUUID());
		version.setCourseId(courseId);
		version.setScope(CurriculumScope.MASTER);
		version.setStatus(CurriculumStatus.PUBLISHED);
		version.setVersionNumber(1);
		version.setCreatedAt(Instant.now());
		version.setUpdatedAt(Instant.now());
		return version;
	}

	private CurriculumSection section(CurriculumVersion version, UUID id, String title, int sortOrder) {
		CurriculumSection section = new CurriculumSection();
		section.setId(id);
		section.setCurriculumVersion(version);
		section.setTitle(title);
		section.setSortOrder(sortOrder);
		section.setCreatedAt(Instant.now());
		section.setUpdatedAt(Instant.now());
		return section;
	}

	private CurriculumLesson publishedLesson(
			CurriculumSection section,
			UUID id,
			String title,
			LessonType type,
			int sortOrder,
			boolean preview) {
		CurriculumLesson lesson = new CurriculumLesson();
		lesson.setId(id);
		lesson.setSection(section);
		lesson.setLessonIdentityId(UUID.randomUUID());
		lesson.setTitle(title);
		lesson.setType(type);
		lesson.setPreview(preview);
		lesson.setStatus(LessonStatus.PUBLISHED);
		lesson.setSortOrder(sortOrder);
		lesson.setCreatedAt(Instant.now());
		lesson.setUpdatedAt(Instant.now());
		return lesson;
	}
}
