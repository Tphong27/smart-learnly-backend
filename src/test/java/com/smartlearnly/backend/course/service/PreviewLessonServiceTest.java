package com.smartlearnly.backend.course.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.course.dto.PreviewLessonResponse;
import com.smartlearnly.backend.course.repository.CourseRepository;
import com.smartlearnly.backend.curriculum.entity.CurriculumLesson;
import com.smartlearnly.backend.curriculum.entity.CurriculumScope;
import com.smartlearnly.backend.curriculum.entity.CurriculumSection;
import com.smartlearnly.backend.curriculum.entity.CurriculumStatus;
import com.smartlearnly.backend.curriculum.entity.CurriculumVersion;
import com.smartlearnly.backend.curriculum.service.CurriculumResolution;
import com.smartlearnly.backend.curriculum.service.CurriculumResolutionService;
import com.smartlearnly.backend.learning.lesson.entity.LessonStatus;
import com.smartlearnly.backend.learning.lesson.entity.LessonType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PreviewLessonServiceTest {
    @Mock
    private CourseRepository courseRepository;
    @Mock
    private CurriculumResolutionService curriculumResolutionService;

    private PreviewLessonService previewLessonService;

    @BeforeEach
    void setUp() {
        previewLessonService = new PreviewLessonService(courseRepository, curriculumResolutionService);
    }

    @Test
    void listPreviewLessonsShouldReturnPublishedPreviewLessonsOnly() {
        UUID courseId = UUID.randomUUID();
        UUID previewLessonId = UUID.randomUUID();

        CurriculumVersion version = version(courseId);
        CurriculumSection section = section(version, 0);
        // Only a PUBLISHED + preview lesson should surface. The other two are filtered out.
        section.getLessons().add(lesson(section, previewLessonId, LessonType.RICH_TEXT, LessonStatus.PUBLISHED, true, 0));
        section.getLessons().add(lesson(section, UUID.randomUUID(), LessonType.VIDEO, LessonStatus.PUBLISHED, false, 1));
        section.getLessons().add(lesson(section, UUID.randomUUID(), LessonType.VIDEO, LessonStatus.DRAFT, true, 2));
        version.getSections().add(section);

        when(courseRepository.existsPublishedById(courseId)).thenReturn(true);
        when(curriculumResolutionService.resolvePublicMaster(courseId))
                .thenReturn(new CurriculumResolution(version, null, null, false,
                        CurriculumResolutionService.SOURCE_MASTER_PUBLIC));

        List<PreviewLessonResponse> response = previewLessonService.listPreviewLessons(courseId);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).lessonId()).isEqualTo(previewLessonId);
        assertThat(response.get(0).lessonType()).isEqualTo("RICH_TEXT");
    }

    @Test
    void listPreviewLessonsShouldHideDraftCourse() {
        UUID courseId = UUID.randomUUID();
        when(courseRepository.existsPublishedById(courseId))
                .thenReturn(false);

        assertThatThrownBy(() -> previewLessonService.listPreviewLessons(courseId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);

        verify(curriculumResolutionService, never()).resolvePublicMaster(courseId);
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

    private CurriculumLesson lesson(
            CurriculumSection section,
            UUID id,
            LessonType type,
            LessonStatus status,
            boolean preview,
            int sortOrder) {
        CurriculumLesson lesson = new CurriculumLesson();
        lesson.setId(id);
        lesson.setSection(section);
        lesson.setLessonIdentityId(UUID.randomUUID());
        lesson.setTitle("Preview");
        lesson.setType(type);
        lesson.setContent("Preview content");
        lesson.setDurationSeconds(600);
        lesson.setPreview(preview);
        lesson.setStatus(status);
        lesson.setSortOrder(sortOrder);
        lesson.setCreatedAt(Instant.now());
        lesson.setUpdatedAt(Instant.now());
        return lesson;
    }
}
