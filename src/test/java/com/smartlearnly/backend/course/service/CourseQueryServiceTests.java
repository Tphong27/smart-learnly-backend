package com.smartlearnly.backend.course.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
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
import com.smartlearnly.backend.course.repository.LearningObjectiveProjection;
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
	private CourseListProjection projection;

	@Mock
	private CourseDetailProjection detailProjection;

	@Mock
	private LearningObjectiveProjection objectiveProjection;

	@Mock
	private CurriculumRowProjection firstLessonRow;

	@Mock
	private CurriculumRowProjection secondLessonRow;

	@Mock
	private CurriculumRowProjection emptyModuleRow;

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

		Page<CourseListItemResponse> result =
				new CourseQueryService(courseRepository, categoryRepository).getCourses(0, 20);

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

		new CourseQueryService(courseRepository, categoryRepository).getCourses(2, 250);

		verify(courseRepository).findPublishedCourses(cappedPageable);
	}

	@Test
	void searchesWithTrimmedLiteralPatternAndMapsProjection() {
		PageRequest cappedPageable = PageRequest.of(1, 100);
		when(courseRepository.searchPublishedCourses(
				"%50\\%\\_off\\\\today%",
				cappedPageable))
				.thenReturn(new PageImpl<>(List.of(projection), cappedPageable, 1));

		Page<CourseListItemResponse> result =
				new CourseQueryService(courseRepository, categoryRepository)
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

		assertThat(org.assertj.core.api.Assertions.catchThrowable(() ->
				new CourseQueryService(courseRepository, categoryRepository)
						.getCoursesByCategory("Java", 0, 20)))
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

		Page<CourseListItemResponse> result =
				new CourseQueryService(courseRepository, categoryRepository)
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
		when(firstLessonRow.getModuleId()).thenReturn(firstModuleId);
		when(firstLessonRow.getModuleTitle()).thenReturn("Getting Started");
		when(firstLessonRow.getModuleOrderIndex()).thenReturn(0);
		when(firstLessonRow.getLessonId()).thenReturn(firstLessonId);
		when(firstLessonRow.getLessonTitle()).thenReturn("Introduction");
		when(firstLessonRow.getLessonType()).thenReturn("video");
		when(firstLessonRow.getLessonOrderIndex()).thenReturn(0);
		when(firstLessonRow.getLessonPreview()).thenReturn(true);
		when(secondLessonRow.getModuleId()).thenReturn(firstModuleId);
		when(secondLessonRow.getLessonId()).thenReturn(secondLessonId);
		when(secondLessonRow.getLessonTitle()).thenReturn("Project Setup");
		when(secondLessonRow.getLessonType()).thenReturn(null);
		when(secondLessonRow.getLessonOrderIndex()).thenReturn(1);
		when(secondLessonRow.getLessonPreview()).thenReturn(false);
		when(emptyModuleRow.getModuleId()).thenReturn(secondModuleId);
		when(emptyModuleRow.getModuleTitle()).thenReturn("Advanced Topics");
		when(emptyModuleRow.getModuleOrderIndex()).thenReturn(1);
		when(emptyModuleRow.getLessonId()).thenReturn(null);
		when(courseRepository.findActiveCurriculumByCourseId(courseId))
				.thenReturn(List.of(firstLessonRow, secondLessonRow, emptyModuleRow));

		CourseDetailResponse result =
				new CourseQueryService(courseRepository, categoryRepository)
						.getCourseDetail("spring-boot-fundamentals");

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
												"video",
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
								List.of()))));
		verify(courseRepository).findPublishedCourseBySlug("spring-boot-fundamentals");
		verify(courseRepository).findLearningObjectivesByCourseId(courseId);
		verify(courseRepository).findActiveCurriculumByCourseId(courseId);
	}

	@Test
	void missingPublishedCourseReturnsNotFoundWithoutLoadingObjectives() {
		when(courseRepository.findPublishedCourseBySlug("missing")).thenReturn(Optional.empty());

		assertThat(org.assertj.core.api.Assertions.catchThrowable(() ->
				new CourseQueryService(courseRepository, categoryRepository)
						.getCourseDetail("missing")))
				.isInstanceOf(ResponseStatusException.class)
				.satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value())
						.isEqualTo(404));

		verify(courseRepository).findPublishedCourseBySlug("missing");
	}

	@Test
	void reservedCourseSlugsReturnNotFoundWithoutRepositoryAccess() {
		CourseQueryService service = new CourseQueryService(courseRepository, categoryRepository);

		assertThat(org.assertj.core.api.Assertions.catchThrowable(() ->
				service.getCourseDetail("search")))
				.isInstanceOf(ResponseStatusException.class);
		assertThat(org.assertj.core.api.Assertions.catchThrowable(() ->
				service.getCourseDetail("category")))
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
}
