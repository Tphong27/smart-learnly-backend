package com.smartlearnly.backend.curriculum.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.common.audit.CourseAuditRecorder;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.course.access.service.CourseAccessService;
import com.smartlearnly.backend.curriculum.dto.LessonRequest;
import com.smartlearnly.backend.curriculum.dto.LessonResourceRequest;
import com.smartlearnly.backend.curriculum.dto.LessonResponse;
import com.smartlearnly.backend.curriculum.dto.ReorderRequest;
import com.smartlearnly.backend.curriculum.dto.SectionRequest;
import com.smartlearnly.backend.course.entity.Category;
import com.smartlearnly.backend.course.entity.Course;
import com.smartlearnly.backend.course.entity.CourseStatus;
import com.smartlearnly.backend.course.repository.CourseRepository;
import com.smartlearnly.backend.curriculum.entity.CurriculumLesson;
import com.smartlearnly.backend.curriculum.entity.CurriculumScope;
import com.smartlearnly.backend.curriculum.entity.CurriculumSection;
import com.smartlearnly.backend.curriculum.entity.CurriculumStatus;
import com.smartlearnly.backend.curriculum.entity.CurriculumVersion;
import com.smartlearnly.backend.curriculum.repository.CurriculumLessonRepository;
import com.smartlearnly.backend.curriculum.repository.CurriculumSectionRepository;
import com.smartlearnly.backend.curriculum.repository.CurriculumVersionRepository;
import com.smartlearnly.backend.curriculum.service.ClassCurriculumCompositionService;
import com.smartlearnly.backend.curriculum.service.CurriculumDtoMapper;
import com.smartlearnly.backend.curriculum.service.CurriculumLessonTestLinkService;
import com.smartlearnly.backend.flashcard.entity.FlashcardSet;
import com.smartlearnly.backend.flashcard.repository.FlashcardSetRepository;
import com.smartlearnly.backend.learning.lesson.entity.LessonStatus;
import com.smartlearnly.backend.learning.lesson.entity.LessonType;
import com.smartlearnly.backend.learning.lesson.service.QuizContentValidator;
import com.smartlearnly.backend.learning.module.repository.CourseModuleRepository;
import com.smartlearnly.backend.user.entity.UserAccount;
import com.smartlearnly.backend.videoai.service.VideoSummaryService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.hibernate.annotations.SQLRestriction;

/**
 * The admin content service authors MASTER {@link CurriculumVersion} snapshots backed by stable
 * module identities, so the setup builds curriculum-domain objects and mocks the
 * curriculum repositories. The original behavioural intent of every case is preserved.
 */
@ExtendWith(MockitoExtension.class)
class CurriculumAdminServicesTest {
    @Mock
    private CourseRepository courseRepository;
    @Mock
    private CurriculumVersionRepository curriculumVersionRepository;
    @Mock
    private CurriculumSectionRepository curriculumSectionRepository;
    @Mock
    private CourseModuleRepository courseModuleRepository;
    @Mock
    private CurriculumLessonRepository curriculumLessonRepository;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private CourseAuditRecorder courseAuditRecorder;
    @Mock
    private QuizContentValidator quizContentValidator;
    @Mock
    private CourseAccessService courseAccessService;
    @Mock
    private VideoSummaryService videoSummaryService;
    @Mock
    private FlashcardSetRepository flashcardSetRepository;
    @Mock
    private CurriculumLessonTestLinkService lessonTestLinkService;

    @Mock
    private ClassCurriculumCompositionService compositionService;

    private CurriculumDtoMapper curriculumDtoMapper;

    private CurriculumSectionAdminService curriculumSectionAdminService;
    private CurriculumLessonAdminService curriculumLessonAdminService;

    @BeforeEach
    void setUp() {
        curriculumDtoMapper = new CurriculumDtoMapper(compositionService);
        MasterCurriculumAccessService curriculumAccessService = new MasterCurriculumAccessService(
                courseRepository,
                curriculumVersionRepository,
                curriculumSectionRepository,
                courseModuleRepository,
                curriculumLessonRepository,
                currentUserService,
                courseAccessService
        );
        curriculumSectionAdminService = new CurriculumSectionAdminService(
                curriculumSectionRepository,
                courseModuleRepository,
                curriculumDtoMapper,
                currentUserService,
                courseAuditRecorder,
                courseAccessService,
                curriculumAccessService
        );
        curriculumLessonAdminService = new CurriculumLessonAdminService(
                curriculumLessonRepository,
                curriculumDtoMapper,
                currentUserService,
                courseAuditRecorder,
                quizContentValidator,
                videoSummaryService,
                flashcardSetRepository,
                courseRepository,
                curriculumAccessService,
                lessonTestLinkService
        );
    }

    @Test
    void reorderSectionsShouldRequireEverySectionExactlyOnce() {
        Course course = course();
        CurriculumVersion version = version(course);
        CurriculumSection first = section(version, 0);
        CurriculumSection second = section(version, 1);
        when(courseRepository.findByIdAndDeletedAtIsNull(course.getId())).thenReturn(Optional.of(course));
        when(curriculumVersionRepository
                .findFirstByCourseIdAndScopeOrderByVersionNumberDescCreatedAtDesc(course.getId(), CurriculumScope.MASTER))
                .thenReturn(Optional.of(version));
        when(curriculumSectionRepository.findByCurriculumVersionIdOrderBySortOrderAscCreatedAtAsc(version.getId()))
                .thenReturn(List.of(first, second));

        assertThatThrownBy(() -> curriculumSectionAdminService.reorderSections(
                course.getId(),
                new ReorderRequest(List.of(first.getId()))
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REQUEST);

        verify(curriculumSectionRepository, never()).saveAll(anyList());
    }

    @Test
    void listLessonsShouldIncludeInactiveLessonsForAdmin() {
        Course course = course();
        CurriculumVersion version = version(course);
        CurriculumSection section = section(version, 0);
        CurriculumLesson draft = lesson(section);
        CurriculumLesson inactive = lesson(section);
        inactive.setStatus(LessonStatus.INACTIVE);
        inactive.setSortOrder(1);
        when(courseRepository.findByIdAndDeletedAtIsNull(course.getId())).thenReturn(Optional.of(course));
        when(curriculumSectionRepository.findById(section.getId())).thenReturn(Optional.of(section));
        when(curriculumLessonRepository.findBySectionIdOrderBySortOrderAscCreatedAtAsc(section.getId()))
                .thenReturn(List.of(draft, inactive));

        List<LessonResponse> response = curriculumLessonAdminService.listLessons(section.getId());

        assertThat(response).hasSize(2);
        assertThat(response).extracting(LessonResponse::status).containsExactly("draft", "inactive");
    }

    @Test
    void deleteLessonShouldMarkItDeletedInsteadOfLeavingAnInactiveRowVisible() {
        Course course = course();
        CurriculumVersion version = version(course);
        CurriculumSection section = section(version, 0);
        CurriculumLesson lesson = lesson(section);
        UserAccount actor = new UserAccount();
        actor.setEmail("admin@smartlearnly.dev");
        when(courseRepository.findByIdAndDeletedAtIsNull(course.getId())).thenReturn(Optional.of(course));
        when(curriculumLessonRepository.findById(lesson.getId())).thenReturn(Optional.of(lesson));
        when(curriculumLessonRepository.save(lesson)).thenReturn(lesson);
        when(currentUserService.requireAuthenticatedUser()).thenReturn(actor);

        curriculumLessonAdminService.deleteLesson(lesson.getId());

        assertThat(lesson.getStatus()).isEqualTo(LessonStatus.INACTIVE);
        assertThat(lesson.getDeletedAt()).isNotNull();
        verify(courseAuditRecorder).recordMaster(
                actor,
                com.smartlearnly.backend.common.audit.AuditAction.LESSON_DELETED,
                "CURRICULUM_LESSON",
                lesson.getId(),
                course.getId(),
                lesson.getTitle());
    }

    @Test
    void curriculumLessonQueriesShouldExcludeSoftDeletedRows() {
        SQLRestriction restriction = CurriculumLesson.class.getAnnotation(SQLRestriction.class);

        assertThat(restriction).isNotNull();
        assertThat(restriction.value()).isEqualTo("deleted_at IS NULL");
    }

    @Test
    void reorderLessonsShouldIncludeInactiveLessonsForAdmin() {
        Course course = course();
        CurriculumVersion version = version(course);
        CurriculumSection section = section(version, 0);
        CurriculumLesson draft = lesson(section);
        CurriculumLesson inactive = lesson(section);
        inactive.setStatus(LessonStatus.INACTIVE);
        inactive.setSortOrder(1);
        UserAccount actor = new UserAccount();
        actor.setEmail("admin@smartlearnly.dev");
        when(courseRepository.findByIdAndDeletedAtIsNull(course.getId())).thenReturn(Optional.of(course));
        when(curriculumSectionRepository.findById(section.getId())).thenReturn(Optional.of(section));
        when(curriculumLessonRepository.findBySectionIdOrderBySortOrderAscCreatedAtAsc(section.getId()))
                .thenReturn(List.of(draft, inactive));
        when(curriculumLessonRepository.saveAll(anyList())).thenReturn(List.of(draft, inactive));
        when(currentUserService.requireAuthenticatedUser()).thenReturn(actor);

        List<LessonResponse> response = curriculumLessonAdminService.reorderLessons(
                section.getId(),
                new ReorderRequest(List.of(inactive.getId(), draft.getId()))
        );

        assertThat(response).extracting(LessonResponse::id).containsExactly(inactive.getId(), draft.getId());
        assertThat(inactive.getSortOrder()).isZero();
        assertThat(draft.getSortOrder()).isEqualTo(1);
    }

    @Test
    void updateLessonShouldAcceptTypeAliasAndReplaceResources() {
        Course course = course();
        CurriculumVersion version = version(course);
        CurriculumSection section = section(version, 0);
        CurriculumLesson lesson = lesson(section);
        UserAccount actor = new UserAccount();
        actor.setEmail("admin@smartlearnly.dev");
        when(courseRepository.findByIdAndDeletedAtIsNull(course.getId())).thenReturn(Optional.of(course));
        when(curriculumLessonRepository.findById(lesson.getId())).thenReturn(Optional.of(lesson));
        when(curriculumLessonRepository.save(lesson)).thenReturn(lesson);
        when(currentUserService.requireAuthenticatedUser()).thenReturn(actor);

        LessonResponse response = curriculumLessonAdminService.updateLesson(
                lesson.getId(),
                new LessonRequest(
                        "Document lesson",
                        null,
                        "DOCUMENT",
                        null,
                        "Summary",
                        "https://storage.test/material.pdf",
                        0,
                        false,
                        "PUBLISHED",
                        List.of(new LessonResourceRequest(
                                "https://storage.test/resource.pdf",
                                "2026/06/resource.pdf",
                                null,
                                "resource.pdf",
                                123L,
                                "application/pdf",
                                null
                        )),
                        null
                )
        );

        assertThat(response.lessonType()).isEqualTo("PDF");
        assertThat(response.resources()).hasSize(1);
        assertThat(response.resources().get(0).name()).isEqualTo("resource.pdf");
        assertThat(response.resources().get(0).sortOrder()).isZero();
    }

    @Test
    void createLessonShouldSupportEveryAdminCurriculumLessonType() {
        Course course = course();
        CurriculumVersion version = version(course);
        CurriculumSection section = section(version, 0);
        UserAccount actor = new UserAccount();
        actor.setEmail("admin@smartlearnly.dev");
        String quizContent = """
                {"questions":[{"question":"2 + 2?","options":["3","4"],"correctIndex":1}]}
                """;

        when(courseRepository.findByIdAndDeletedAtIsNull(course.getId())).thenReturn(Optional.of(course));
        when(curriculumSectionRepository.findById(section.getId())).thenReturn(Optional.of(section));
        when(curriculumLessonRepository.findMaxSortOrderBySectionId(section.getId())).thenReturn(0);
        when(curriculumLessonRepository.save(any(CurriculumLesson.class))).thenAnswer(invocation -> {
            CurriculumLesson saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            saved.setCreatedAt(Instant.now());
            saved.setUpdatedAt(Instant.now());
            return saved;
        });
        when(currentUserService.requireAuthenticatedUser()).thenReturn(actor);

        record LessonCase(String type, String content, String videoUrl) {}
        List<LessonCase> cases = List.of(
                new LessonCase("VIDEO", null, null),
                new LessonCase("RICH_TEXT", "<p>Text lesson</p>", null),
                new LessonCase("QUIZ", quizContent, null),
                new LessonCase("FLASHCARD", null, null),
                new LessonCase("ESSAY", "Assignment instructions", null));

        List<LessonResponse> responses = cases.stream()
                .map(testCase -> curriculumLessonAdminService.createLesson(
                        section.getId(),
                        new LessonRequest(
                                testCase.type() + " lesson",
                                testCase.type(),
                                null,
                                testCase.videoUrl(),
                                testCase.content(),
                                null,
                                60,
                                false,
                                "PUBLISHED",
                                List.of(),
                                null)))
                .toList();

        assertThat(responses)
                .extracting(LessonResponse::lessonType)
                .containsExactly("VIDEO", "RICH_TEXT", "QUIZ", "FLASHCARD", "ESSAY");
        assertThat(responses).allMatch(response -> "published".equals(response.status()));
        assertThat(responses).allMatch(response -> response.id() != null);
        assertThat(responses.get(0).videoUrl()).isNull();
        verify(videoSummaryService, never())
                .normalizeLessonVideoUrl(null, null, true);
        verify(quizContentValidator).validate(quizContent.trim());
    }

    @Test
    void createFlashcardLessonShouldCreateLinkedSet() {
        Course course = course();
        CurriculumVersion version = version(course);
        CurriculumSection section = section(version, 0);
        UserAccount actor = new UserAccount();
        actor.setId(UUID.randomUUID());
        actor.setEmail("admin@smartlearnly.dev");

        when(courseRepository.findByIdAndDeletedAtIsNull(course.getId())).thenReturn(Optional.of(course));
        when(curriculumSectionRepository.findById(section.getId())).thenReturn(Optional.of(section));
        when(curriculumLessonRepository.findMaxSortOrderBySectionId(section.getId())).thenReturn(0);
        when(curriculumLessonRepository.save(any(CurriculumLesson.class))).thenAnswer(invocation -> {
            CurriculumLesson saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            saved.setCreatedAt(Instant.now());
            saved.setUpdatedAt(Instant.now());
            return saved;
        });
        when(currentUserService.requireAuthenticatedUser()).thenReturn(actor);

        LessonResponse response = curriculumLessonAdminService.createLesson(
                section.getId(),
                new LessonRequest(
                        "Vocabulary",
                        "FLASHCARD",
                        null,
                        null,
                        null,
                        null,
                        0,
                        false,
                        "DRAFT",
                        List.of(),
                        null));

        ArgumentCaptor<FlashcardSet> setCaptor = ArgumentCaptor.forClass(FlashcardSet.class);
        verify(flashcardSetRepository).save(setCaptor.capture());
        FlashcardSet savedSet = setCaptor.getValue();
        assertThat(savedSet.getCurriculumLessonId()).isEqualTo(response.id());
        assertThat(savedSet.getCourse()).isSameAs(course);
        assertThat(savedSet.getCreatedBy()).isSameAs(actor);
        assertThat(savedSet.getTitle()).isEqualTo("Vocabulary");
        assertThat(savedSet.getIsPublic()).isFalse();
        assertThat(savedSet.getIsOfficial()).isFalse();
    }

    @Test
    void updateLessonToFlashcardShouldCreateMissingLinkedSet() {
        Course course = course();
        CurriculumVersion version = version(course);
        CurriculumSection section = section(version, 0);
        CurriculumLesson lesson = lesson(section);
        UserAccount actor = new UserAccount();
        actor.setId(UUID.randomUUID());
        actor.setEmail("admin@smartlearnly.dev");

        when(courseRepository.findByIdAndDeletedAtIsNull(course.getId())).thenReturn(Optional.of(course));
        when(curriculumLessonRepository.findById(lesson.getId())).thenReturn(Optional.of(lesson));
        when(curriculumLessonRepository.save(lesson)).thenReturn(lesson);
        when(currentUserService.requireAuthenticatedUser()).thenReturn(actor);

        curriculumLessonAdminService.updateLesson(
                lesson.getId(),
                new LessonRequest(
                        "Converted flashcards",
                        "FLASHCARD",
                        null,
                        null,
                        null,
                        null,
                        0,
                        false,
                        "DRAFT",
                        List.of(),
                        null));

        ArgumentCaptor<FlashcardSet> setCaptor = ArgumentCaptor.forClass(FlashcardSet.class);
        verify(flashcardSetRepository).save(setCaptor.capture());
        assertThat(setCaptor.getValue().getCurriculumLessonId()).isEqualTo(lesson.getId());
        assertThat(setCaptor.getValue().getTitle()).isEqualTo("Converted flashcards");
    }

    @Test
    void createSectionShouldCreatePublishedMasterForPublishedCourse() {
        Course course = course();
        course.setStatus(CourseStatus.PUBLISHED);
        UserAccount actor = new UserAccount();
        actor.setId(UUID.randomUUID());
        actor.setEmail("admin@smartlearnly.dev");

        when(courseRepository.findByIdAndDeletedAtIsNull(course.getId())).thenReturn(Optional.of(course));
        when(curriculumVersionRepository
                .findFirstByCourseIdAndScopeOrderByVersionNumberDescCreatedAtDesc(
                        course.getId(), CurriculumScope.MASTER))
                .thenReturn(Optional.empty());
        when(curriculumVersionRepository.findMaxMasterVersionNumber(course.getId(), CurriculumScope.MASTER))
                .thenReturn(0);
        when(curriculumVersionRepository.save(any(CurriculumVersion.class))).thenAnswer(invocation -> {
            CurriculumVersion version = invocation.getArgument(0);
            version.setId(UUID.randomUUID());
            return version;
        });
        when(courseModuleRepository.save(any())).thenAnswer(invocation -> {
            var module = invocation.getArgument(0, com.smartlearnly.backend.learning.module.entity.CourseModule.class);
            module.setId(UUID.randomUUID());
            return module;
        });
        when(curriculumSectionRepository.save(any(CurriculumSection.class))).thenAnswer(invocation -> {
            CurriculumSection section = invocation.getArgument(0);
            section.setId(UUID.randomUUID());
            section.setCreatedAt(Instant.now());
            section.setUpdatedAt(Instant.now());
            return section;
        });
        when(currentUserService.requireAuthenticatedUser()).thenReturn(actor);

        curriculumSectionAdminService.createSection(course.getId(), new SectionRequest("Introduction", 0));

        ArgumentCaptor<CurriculumVersion> versionCaptor = ArgumentCaptor.forClass(CurriculumVersion.class);
        verify(curriculumVersionRepository).save(versionCaptor.capture());
        assertThat(versionCaptor.getValue().getStatus()).isEqualTo(CurriculumStatus.PUBLISHED);
        assertThat(versionCaptor.getValue().getPublishedAt()).isNotNull();
    }

    private Course course() {
        Category category = new Category();
        category.setId(UUID.randomUUID());
        category.setName("Programming");

        Course course = new Course();
        course.setId(UUID.randomUUID());
        course.setTitle("Course");
        course.setSlug("course");
        course.setCategory(category);
        course.setPrice(BigDecimal.ZERO);
        course.setFree(true);
        course.setStatus(CourseStatus.DRAFT);
        course.setCreatedAt(Instant.now());
        course.setUpdatedAt(Instant.now());
        return course;
    }

    private CurriculumVersion version(Course course) {
        CurriculumVersion version = new CurriculumVersion();
        version.setId(UUID.randomUUID());
        version.setCourseId(course.getId());
        version.setScope(CurriculumScope.MASTER);
        version.setStatus(CurriculumStatus.DRAFT);
        version.setVersionNumber(1);
        version.setTitle(course.getTitle());
        version.setCreatedAt(Instant.now());
        version.setUpdatedAt(Instant.now());
        return version;
    }

    private CurriculumSection section(CurriculumVersion version, int sortOrder) {
        CurriculumSection section = new CurriculumSection();
        section.setId(UUID.randomUUID());
        section.setCurriculumVersion(version);
        section.setTitle("Section " + sortOrder);
        section.setSortOrder(sortOrder);
        section.setCreatedAt(Instant.now());
        section.setUpdatedAt(Instant.now());
        return section;
    }

    private CurriculumLesson lesson(CurriculumSection section) {
        CurriculumLesson lesson = new CurriculumLesson();
        lesson.setId(UUID.randomUUID());
        lesson.setSection(section);
        lesson.setLessonIdentityId(UUID.randomUUID());
        lesson.setTitle("Lesson");
        lesson.setType(LessonType.RICH_TEXT);
        lesson.setPreview(false);
        lesson.setStatus(LessonStatus.DRAFT);
        lesson.setSortOrder(0);
        lesson.setCreatedAt(Instant.now());
        lesson.setUpdatedAt(Instant.now());
        return lesson;
    }
}
