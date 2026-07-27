package com.smartlearnly.backend.lessonprogress.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.assignment.repository.AssignmentRepository;
import com.smartlearnly.backend.assignment.repository.AssignmentSubmissionRepository;
import com.smartlearnly.backend.classroom.repository.ClassOfferingRepository;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.curriculum.entity.CurriculumLesson;
import com.smartlearnly.backend.curriculum.entity.CurriculumScope;
import com.smartlearnly.backend.curriculum.entity.CurriculumSection;
import com.smartlearnly.backend.curriculum.entity.CurriculumStatus;
import com.smartlearnly.backend.curriculum.entity.CurriculumVersion;
import com.smartlearnly.backend.curriculum.repository.CurriculumLessonRepository;
import com.smartlearnly.backend.curriculum.service.CurriculumResolution;
import com.smartlearnly.backend.curriculum.service.CurriculumResolutionService;
import com.smartlearnly.backend.enrollment.dto.MyCourseClassResponse;
import com.smartlearnly.backend.enrollment.dto.MyCourseResponse;
import com.smartlearnly.backend.enrollment.service.CourseEnrollmentService;
import com.smartlearnly.backend.learning.lesson.entity.LessonStatus;
import com.smartlearnly.backend.learning.lesson.entity.LessonType;
import com.smartlearnly.backend.lessonprogress.dto.CourseProgressItemResponse;
import com.smartlearnly.backend.lessonprogress.dto.TraineeProgressResponse;
import com.smartlearnly.backend.lessonprogress.entity.LessonProgress;
import com.smartlearnly.backend.lessonprogress.repository.LessonProgressRepository;
import com.smartlearnly.backend.user.entity.UserAccount;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KhiemTraineeProgressReportTest {

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private CourseEnrollmentService courseEnrollmentService;

    @Mock
    private LessonProgressRepository lessonProgressRepository;

    @Mock
    private ClassOfferingRepository classOfferingRepository;

    @Mock
    private CurriculumResolutionService curriculumResolutionService;

    @Mock
    private CurriculumLessonRepository curriculumLessonRepository;

    @Mock
    private AssignmentRepository assignmentRepository;

    @Mock
    private AssignmentSubmissionRepository assignmentSubmissionRepository;

    @InjectMocks
    private TraineeProgressService service;

    private UUID studentId;
    private UserAccount student;

    @BeforeEach
    void setUp() {
        studentId = UUID.randomUUID();
        student = new UserAccount();
        student.setId(studentId);
    }

    @Test
    void UTCID_KHIEM_BE_473_getMyProgress_returnsZeroSummaryForEmptyEnrollment() {
        when(currentUserService.requireAuthenticatedUser()).thenReturn(student);
        when(courseEnrollmentService.getMyCourses()).thenReturn(List.of());

        TraineeProgressResponse response = service.getMyProgress();

        assertThat(response.totalCourses()).isZero();
        assertThat(response.completedCourses()).isZero();
        assertThat(response.inProgressCourses()).isZero();
        assertThat(response.courses()).isEmpty();
        assertThat(response.completedCourseItems()).isEmpty();
        assertThat(response.inProgressCourseItems()).isEmpty();
    }

    @Test
    void UTCID_KHIEM_BE_474_getMyProgress_classifiesCompletedOnlineCourse() {
        UUID courseId = UUID.randomUUID();
        CurriculumLesson video = lesson(
                LessonType.VIDEO,
                LessonStatus.PUBLISHED);
        CurriculumVersion version = version(courseId, video);
        when(currentUserService.requireAuthenticatedUser()).thenReturn(student);
        when(courseEnrollmentService.getMyCourses())
                .thenReturn(List.of(onlineCourse(courseId, "Online Java")));
        when(curriculumResolutionService.resolveOnlineLearning(courseId, studentId))
                .thenReturn(resolution(version, null));
        when(lessonProgressRepository
                .findByStudentIdAndCourseIdAndClassIdIsNull(studentId, courseId))
                .thenReturn(List.of(completedProgress(video)));

        TraineeProgressResponse response = service.getMyProgress();

        assertThat(response.totalCourses()).isEqualTo(1);
        assertThat(response.completedCourses()).isEqualTo(1);
        assertThat(response.inProgressCourses()).isZero();
        assertThat(response.completedCourseItems())
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.courseId()).isEqualTo(courseId);
                    assertThat(item.classId()).isNull();
                    assertThat(item.overallPercent()).isEqualTo(100);
                    assertThat(item.courseStatus()).isEqualTo("COMPLETED");
                });
    }

    @Test
    void UTCID_KHIEM_BE_475_getMyProgress_separatesCompletedOnlineAndPartialClass() {
        UUID onlineCourseId = UUID.randomUUID();
        UUID classCourseId = UUID.randomUUID();
        UUID classId = UUID.randomUUID();
        CurriculumLesson onlineVideo = lesson(
                LessonType.VIDEO,
                LessonStatus.PUBLISHED);
        CurriculumVersion onlineVersion = version(onlineCourseId, onlineVideo);
        CurriculumLesson classVideo1 = lesson(
                LessonType.VIDEO,
                LessonStatus.PUBLISHED);
        CurriculumLesson classVideo2 = lesson(
                LessonType.PDF,
                LessonStatus.PUBLISHED);
        CurriculumVersion classVersion = version(
                classCourseId,
                classVideo1,
                classVideo2);

        when(currentUserService.requireAuthenticatedUser()).thenReturn(student);
        when(courseEnrollmentService.getMyCourses())
                .thenReturn(List.of(
                        onlineCourse(onlineCourseId, "Online Java"),
                        classCourse(classCourseId, classId, "Java Class A")));
        when(curriculumResolutionService
                .resolveOnlineLearning(onlineCourseId, studentId))
                .thenReturn(resolution(onlineVersion, null));
        when(curriculumResolutionService
                .resolveTraineeProgress(classCourseId, classId, studentId))
                .thenReturn(resolution(classVersion, classId));
        when(lessonProgressRepository
                .findByStudentIdAndCourseIdAndClassIdIsNull(
                        studentId,
                        onlineCourseId))
                .thenReturn(List.of(completedProgress(onlineVideo)));
        when(lessonProgressRepository.findByStudentIdAndClassIdAndCourseId(
                studentId,
                classId,
                classCourseId))
                .thenReturn(List.of(completedProgress(classVideo1)));
        when(assignmentRepository.findAvailableForStudent(
                studentId,
                classCourseId,
                classId,
                false))
                .thenReturn(List.of());

        TraineeProgressResponse response = service.getMyProgress();

        assertThat(response.totalCourses()).isEqualTo(2);
        assertThat(response.completedCourses()).isEqualTo(1);
        assertThat(response.inProgressCourses()).isEqualTo(1);
        assertThat(response.completedCourseItems())
                .extracting(CourseProgressItemResponse::courseId)
                .containsExactly(onlineCourseId);
        assertThat(response.inProgressCourseItems())
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.courseId()).isEqualTo(classCourseId);
                    assertThat(item.classId()).isEqualTo(classId);
                    assertThat(item.overallPercent()).isEqualTo(50);
                    assertThat(item.courseStatus()).isEqualTo("IN_PROGRESS");
                });
    }

    private MyCourseResponse onlineCourse(UUID courseId, String title) {
        return new MyCourseResponse(
                courseId,
                title,
                title.toLowerCase().replace(" ", "-"),
                null,
                BigDecimal.ZERO,
                null,
                false,
                null,
                UUID.randomUUID(),
                "ACTIVE",
                Instant.now(),
                "PUBLISHED",
                true,
                null,
                null);
    }

    private MyCourseResponse classCourse(
            UUID courseId,
            UUID classId,
            String className) {
        MyCourseClassResponse enrolledClass = new MyCourseClassResponse(
                classId,
                className,
                "upcoming",
                "Trainer",
                "Monday 08:00-10:00",
                LocalDate.now().plusDays(1),
                LocalDate.now().plusMonths(1),
                30,
                1L,
                UUID.randomUUID());

        return new MyCourseResponse(
                courseId,
                "Class-based Java",
                "class-based-java",
                null,
                BigDecimal.ZERO,
                null,
                false,
                null,
                UUID.randomUUID(),
                "ACTIVE",
                Instant.now(),
                "PUBLISHED",
                true,
                null,
                enrolledClass);
    }

    private CurriculumResolution resolution(
            CurriculumVersion version,
            UUID classId) {
        return new CurriculumResolution(
                version,
                null,
                classId,
                false,
                "UNIT_TEST");
    }

    private CurriculumVersion version(
            UUID courseId,
            CurriculumLesson... lessons) {
        CurriculumVersion version = new CurriculumVersion();
        version.setId(UUID.randomUUID());
        version.setCourseId(courseId);
        version.setScope(CurriculumScope.MASTER);
        version.setStatus(CurriculumStatus.PUBLISHED);
        version.setVersionNumber(1);

        CurriculumSection section = new CurriculumSection();
        section.setId(UUID.randomUUID());
        section.setTitle("Module 1");
        section.setSortOrder(0);
        version.addSection(section);

        for (int index = 0; index < lessons.length; index += 1) {
            lessons[index].setSortOrder(index);
            section.addLesson(lessons[index]);
        }
        return version;
    }

    private CurriculumLesson lesson(
            LessonType type,
            LessonStatus status) {
        CurriculumLesson lesson = new CurriculumLesson();
        lesson.setId(UUID.randomUUID());
        lesson.setLessonIdentityId(UUID.randomUUID());
        lesson.setTitle(type.name());
        lesson.setType(type);
        lesson.setStatus(status);
        lesson.setPreview(false);
        lesson.setSortOrder(0);
        return lesson;
    }

    private LessonProgress completedProgress(CurriculumLesson lesson) {
        LessonProgress progress = new LessonProgress();
        progress.setStudentId(studentId);
        progress.setLessonId(lesson.getId());
        progress.setLessonIdentityId(lesson.getLessonIdentityId());
        progress.setCompleted(true);
        progress.setCompletedAt(Instant.now());
        return progress;
    }
}
