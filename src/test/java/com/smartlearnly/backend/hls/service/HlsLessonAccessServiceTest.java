package com.smartlearnly.backend.hls.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.course.entity.Course;
import com.smartlearnly.backend.course.service.CourseAccessService;
import com.smartlearnly.backend.curriculum.entity.CurriculumLesson;
import com.smartlearnly.backend.curriculum.entity.CurriculumScope;
import com.smartlearnly.backend.curriculum.entity.CurriculumSection;
import com.smartlearnly.backend.curriculum.entity.CurriculumVersion;
import com.smartlearnly.backend.curriculum.repository.CurriculumLessonRepository;
import com.smartlearnly.backend.curriculum.service.TrainerClassCurriculumService;
import com.smartlearnly.backend.learning.lesson.entity.Lesson;
import com.smartlearnly.backend.learning.lesson.entity.LessonType;
import com.smartlearnly.backend.learning.lesson.repository.LessonRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HlsLessonAccessServiceTest {
    @Mock
    private LessonRepository lessonRepository;
    @Mock
    private CurriculumLessonRepository curriculumLessonRepository;
    @Mock
    private CourseAccessService courseAccessService;
    @Mock
    private TrainerClassCurriculumService trainerClassCurriculumService;

    private HlsLessonAccessService service;

    @BeforeEach
    void setUp() {
        service = new HlsLessonAccessService(
                lessonRepository,
                curriculumLessonRepository,
                courseAccessService,
                trainerClassCurriculumService
        );
    }

    @Test
    void writableMasterLessonRequiresCourseUpdateAccess() {
        UUID lessonId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        Course course = new Course();
        course.setId(courseId);
        Lesson lesson = new Lesson();
        lesson.setId(lessonId);
        lesson.setCourse(course);
        lesson.setType(LessonType.VIDEO);
        when(lessonRepository.findById(lessonId)).thenReturn(Optional.of(lesson));

        service.requireWritableVideo(lessonId);

        verify(courseAccessService).requireUpdatableCourse(courseId);
    }

    @Test
    void writableClassLessonRequiresOwnedDraftLesson() {
        UUID lessonId = UUID.randomUUID();
        UUID classId = UUID.randomUUID();
        CurriculumLesson lesson = curriculumLesson(lessonId, CurriculumScope.CLASS, classId);
        when(lessonRepository.findById(lessonId)).thenReturn(Optional.empty());
        when(curriculumLessonRepository.findById(lessonId)).thenReturn(Optional.of(lesson));

        service.requireWritableVideo(lessonId);

        verify(trainerClassCurriculumService).requireOwnedClassLessonForWrite(classId, lessonId);
    }

    @Test
    void readableClassLessonRequiresOwnedActiveLesson() {
        UUID lessonId = UUID.randomUUID();
        UUID classId = UUID.randomUUID();
        CurriculumLesson lesson = curriculumLesson(lessonId, CurriculumScope.CLASS, classId);
        when(lessonRepository.findById(lessonId)).thenReturn(Optional.empty());
        when(curriculumLessonRepository.findById(lessonId)).thenReturn(Optional.of(lesson));

        service.requireReadable(lessonId);

        verify(trainerClassCurriculumService).requireOwnedClassLessonForRead(classId, lessonId);
    }

    @Test
    void uploadRejectsNonVideoLessons() {
        UUID lessonId = UUID.randomUUID();
        Lesson lesson = new Lesson();
        lesson.setId(lessonId);
        lesson.setType(LessonType.RICH_TEXT);
        lesson.setCourse(new Course());
        when(lessonRepository.findById(lessonId)).thenReturn(Optional.of(lesson));

        assertThatThrownBy(() -> service.requireWritableVideo(lessonId))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.INVALID_REQUEST));
    }

    private CurriculumLesson curriculumLesson(UUID lessonId, CurriculumScope scope, UUID classId) {
        CurriculumVersion version = new CurriculumVersion();
        version.setId(UUID.randomUUID());
        version.setCourseId(UUID.randomUUID());
        version.setClassId(classId);
        version.setScope(scope);
        CurriculumSection section = new CurriculumSection();
        section.setId(UUID.randomUUID());
        section.setCurriculumVersion(version);
        CurriculumLesson lesson = new CurriculumLesson();
        lesson.setId(lessonId);
        lesson.setSection(section);
        lesson.setType(LessonType.VIDEO);
        return lesson;
    }
}
