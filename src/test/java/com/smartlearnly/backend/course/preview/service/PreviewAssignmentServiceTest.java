package com.smartlearnly.backend.course.preview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.assignment.definition.dto.AssignmentModel;
import com.smartlearnly.backend.assignment.definition.service.AssignmentService;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.course.entity.Course;
import com.smartlearnly.backend.course.entity.CourseStatus;
import com.smartlearnly.backend.course.repository.CourseRepository;
import com.smartlearnly.backend.curriculum.entity.CurriculumLesson;
import com.smartlearnly.backend.curriculum.entity.CurriculumSection;
import com.smartlearnly.backend.curriculum.entity.CurriculumVersion;
import com.smartlearnly.backend.curriculum.service.ClassCurriculumCompositionService;
import com.smartlearnly.backend.curriculum.service.CurriculumResolution;
import com.smartlearnly.backend.curriculum.service.CurriculumResolutionService;
import com.smartlearnly.backend.learning.lesson.entity.LessonStatus;
import com.smartlearnly.backend.learning.lesson.entity.LessonType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PreviewAssignmentServiceTest {
    @Mock
    private CourseRepository courseRepository;
    @Mock
    private CurriculumResolutionService curriculumResolutionService;
    @Mock
    private ClassCurriculumCompositionService compositionService;
    @Mock
    private AssignmentService assignmentService;

    private PreviewAssignmentService service;

    @BeforeEach
    void setUp() {
        service = new PreviewAssignmentService(
                courseRepository,
                curriculumResolutionService,
                compositionService,
                assignmentService);
    }

    @Test
    void getPreviewAssignmentReturnsOnlyReadOnlyFieldsForPublishedPreviewLesson() {
        UUID courseId = UUID.randomUUID();
        UUID lessonId = UUID.randomUUID();
        CurriculumVersion version = publishedAssignmentVersion(lessonId, true);
        Course course = new Course();
        course.setId(courseId);
        course.setStatus(CourseStatus.PUBLISHED);

        AssignmentModel.Response assignment = new AssignmentModel.Response();
        assignment.setId(UUID.randomUUID());
        assignment.setCourseId(courseId);
        assignment.setLessonId(lessonId);
        assignment.setTitle("Course essay");
        assignment.setDescription("Write an essay");
        assignment.setMaxScore(BigDecimal.TEN);

        when(courseRepository.findByIdAndDeletedAtIsNull(courseId)).thenReturn(Optional.of(course));
        when(curriculumResolutionService.resolvePublicMaster(courseId))
                .thenReturn(new CurriculumResolution(version, null, null, false, "public_master"));
        when(assignmentService.findAssignmentByLessonId(lessonId, null))
                .thenReturn(Optional.of(assignment));

        var response = service.getPreviewAssignment(courseId, null, lessonId);

        assertThat(response.title()).isEqualTo("Course essay");
        assertThat(response.description()).isEqualTo("Write an essay");
        assertThat(response.maxScore()).isEqualByComparingTo(BigDecimal.TEN);
        verify(assignmentService).findAssignmentByLessonId(lessonId, null);
    }

    @Test
    void getPreviewAssignmentRejectsLessonThatIsNotMarkedForPreview() {
        UUID courseId = UUID.randomUUID();
        UUID lessonId = UUID.randomUUID();
        CurriculumVersion version = publishedAssignmentVersion(lessonId, false);
        Course course = new Course();
        course.setId(courseId);
        course.setStatus(CourseStatus.PUBLISHED);

        when(courseRepository.findByIdAndDeletedAtIsNull(courseId)).thenReturn(Optional.of(course));
        when(curriculumResolutionService.resolvePublicMaster(courseId))
                .thenReturn(new CurriculumResolution(version, null, null, false, "public_master"));

        assertThatThrownBy(() -> service.getPreviewAssignment(courseId, null, lessonId))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Preview assignment was not found");
    }

    /** Tạo curriculum tối thiểu để kiểm thử đúng điều kiện public preview. */
    private CurriculumVersion publishedAssignmentVersion(UUID lessonId, boolean preview) {
        CurriculumVersion version = new CurriculumVersion();
        CurriculumSection section = new CurriculumSection();
        section.setCurriculumVersion(version);

        CurriculumLesson lesson = new CurriculumLesson();
        lesson.setId(lessonId);
        lesson.setType(LessonType.ASSIGNMENT);
        lesson.setStatus(LessonStatus.PUBLISHED);
        lesson.setPreview(preview);

        section.setLessons(List.of(lesson));
        version.setSections(List.of(section));
        return version;
    }
}
