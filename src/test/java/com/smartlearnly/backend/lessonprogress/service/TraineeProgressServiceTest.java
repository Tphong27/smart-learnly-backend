package com.smartlearnly.backend.lessonprogress.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.classroom.entity.ClassOffering;
import com.smartlearnly.backend.classroom.repository.ClassOfferingRepository;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.curriculum.entity.CurriculumLesson;
import com.smartlearnly.backend.curriculum.entity.CurriculumScope;
import com.smartlearnly.backend.curriculum.entity.CurriculumStatus;
import com.smartlearnly.backend.curriculum.entity.CurriculumVersion;
import com.smartlearnly.backend.curriculum.repository.CurriculumLessonRepository;
import com.smartlearnly.backend.curriculum.service.CurriculumResolution;
import com.smartlearnly.backend.curriculum.service.CurriculumResolutionService;
import com.smartlearnly.backend.enrollment.service.CourseEnrollmentService;
import com.smartlearnly.backend.learning.lesson.entity.LessonStatus;
import com.smartlearnly.backend.learning.lesson.entity.LessonType;
import com.smartlearnly.backend.learning.lesson.repository.LessonRepository;
import com.smartlearnly.backend.learning.module.repository.CourseSectionRepository;
import com.smartlearnly.backend.lessonprogress.dto.LessonProgressResponse;
import com.smartlearnly.backend.lessonprogress.entity.LessonProgress;
import com.smartlearnly.backend.lessonprogress.repository.LessonProgressRepository;
import com.smartlearnly.backend.user.entity.UserAccount;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Covers OpenSpec task 8.2: lesson progress is keyed by (classId + lessonIdentityId) so that completing
 * a lesson in one class never marks the same lesson complete in a different class.
 */
@ExtendWith(MockitoExtension.class)
class TraineeProgressServiceTest {
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private CourseEnrollmentService courseEnrollmentService;
    @Mock
    private CourseSectionRepository courseSectionRepository;
    @Mock
    private LessonRepository lessonRepository;
    @Mock
    private LessonProgressRepository lessonProgressRepository;
    @Mock
    private ClassOfferingRepository classOfferingRepository;
    @Mock
    private CurriculumResolutionService curriculumResolutionService;
    @Mock
    private CurriculumLessonRepository curriculumLessonRepository;

    @InjectMocks
    private TraineeProgressService traineeProgressService;

    @Test
    void completingLessonInOneClassDoesNotMarkItCompleteInAnotherClass() {
        UUID studentId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID classA = UUID.randomUUID();
        UUID classB = UUID.randomUUID();
        // Same lesson identity is shared by both classes (the curriculum was inherited from master).
        UUID lessonIdentityId = UUID.randomUUID();
        UUID lessonRowIdA = UUID.randomUUID();

        UserAccount student = new UserAccount();
        student.setId(studentId);
        when(currentUserService.requireAuthenticatedUser()).thenReturn(student);

        ClassOffering offeringA = classOffering(classA, courseId);
        when(classOfferingRepository.findByIdAndDeletedAtIsNull(classA)).thenReturn(Optional.of(offeringA));

        CurriculumVersion versionA = version(courseId);
        when(curriculumResolutionService.resolveTraineeLearning(courseId, classA, studentId))
                .thenReturn(new CurriculumResolution(versionA, null, classA, false,
                        CurriculumResolutionService.SOURCE_MASTER_INHERITED));

        CurriculumLesson lessonA = lesson(lessonRowIdA, lessonIdentityId);
        when(curriculumLessonRepository.findEffectiveLessonReference(versionA.getId(), lessonIdentityId))
                .thenReturn(Optional.of(lessonA));

        // No prior progress for this lesson identity in class A.
        when(lessonProgressRepository
                .findByStudentIdAndClassIdAndLessonIdentityId(studentId, classA, lessonIdentityId))
                .thenReturn(Optional.empty());
        when(lessonProgressRepository.save(any(LessonProgress.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LessonProgressResponse response = traineeProgressService
                .updateLessonProgress(lessonIdentityId, courseId, classA, true);

        // Progress is persisted scoped to class A and the shared lesson identity.
        ArgumentCaptor<LessonProgress> captor = ArgumentCaptor.forClass(LessonProgress.class);
        verify(lessonProgressRepository).save(captor.capture());
        LessonProgress saved = captor.getValue();
        assertThat(saved.getLessonId()).isEqualTo(lessonRowIdA);
        assertThat(saved.getClassId()).isEqualTo(classA);
        assertThat(saved.getLessonIdentityId()).isEqualTo(lessonIdentityId);
        assertThat(saved.isCompleted()).isTrue();
        assertThat(response.classId()).isEqualTo(classA);
        assertThat(response.completed()).isTrue();

        // The lookup is keyed by (student, classId, lessonIdentityId): a query for class B is a distinct key,
        // so the class-A completion above cannot satisfy it.
        verify(lessonProgressRepository)
                .findByStudentIdAndClassIdAndLessonIdentityId(studentId, classA, lessonIdentityId);
        assertThat(lessonProgressRepository
                .findByStudentIdAndClassIdAndLessonIdentityId(studentId, classB, lessonIdentityId))
                .isEmpty();
    }

    private ClassOffering classOffering(UUID classId, UUID courseId) {
        ClassOffering classOffering = new ClassOffering();
        classOffering.setId(classId);
        classOffering.setCourseId(courseId);
        return classOffering;
    }

    private CurriculumVersion version(UUID courseId) {
        CurriculumVersion version = new CurriculumVersion();
        version.setId(UUID.randomUUID());
        version.setCourseId(courseId);
        version.setScope(CurriculumScope.MASTER);
        version.setStatus(CurriculumStatus.PUBLISHED);
        version.setVersionNumber(1);
        return version;
    }

    private CurriculumLesson lesson(UUID id, UUID lessonIdentityId) {
        CurriculumLesson lesson = new CurriculumLesson();
        lesson.setId(id);
        lesson.setLessonIdentityId(lessonIdentityId);
        lesson.setTitle("Lesson");
        lesson.setType(LessonType.VIDEO);
        lesson.setStatus(LessonStatus.PUBLISHED);
        lesson.setPreview(false);
        lesson.setSortOrder(0);
        return lesson;
    }
}
