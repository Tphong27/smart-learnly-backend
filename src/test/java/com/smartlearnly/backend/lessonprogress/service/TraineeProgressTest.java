package com.smartlearnly.backend.lessonprogress.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.assignment.entity.Assignment;
import com.smartlearnly.backend.assignment.entity.AssignmentSubmission;
import com.smartlearnly.backend.assignment.entity.SubmissionStatus;
import com.smartlearnly.backend.assignment.repository.AssignmentRepository;
import com.smartlearnly.backend.assignment.repository.AssignmentSubmissionRepository;
import com.smartlearnly.backend.classroom.entity.ClassOffering;
import com.smartlearnly.backend.classroom.repository.ClassOfferingRepository;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
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
import com.smartlearnly.backend.lessonprogress.dto.LessonProgressResponse;
import com.smartlearnly.backend.lessonprogress.dto.TraineeProgressResponse;
import com.smartlearnly.backend.lessonprogress.entity.LessonProgress;
import com.smartlearnly.backend.lessonprogress.repository.LessonProgressRepository;
import com.smartlearnly.backend.user.entity.UserAccount;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TraineeProgressTest {

        private static final UUID STUDENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
        private static final UUID ONLINE_COURSE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
        private static final UUID CLASS_COURSE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
        private static final UUID CLASS_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
        private static final UUID ACTUAL_COURSE_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
        private static final UUID REQUESTED_COURSE_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");
        private static final UUID LESSON_IDENTITY_ID = UUID.fromString("77777777-7777-7777-7777-777777777777");
        private static final UUID TRAINER_ID = UUID.fromString("88888888-8888-8888-8888-888888888888");
        private static final UUID ENROLLMENT_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");
        private static final UUID VERSION_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        private static final UUID SECTION_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        private static final UUID PRIMARY_LESSON_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        private static final UUID PRIMARY_LESSON_IDENTITY_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
        private static final UUID SECOND_LESSON_ID = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
        private static final UUID SECOND_LESSON_IDENTITY_ID = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
        private static final UUID THIRD_LESSON_ID = UUID.fromString("01234567-89ab-cdef-0123-456789abcdef");
        private static final UUID THIRD_LESSON_IDENTITY_ID = UUID.fromString("12345678-9abc-def0-1234-56789abcdef0");
        private static final UUID ASSIGNMENT_ONE_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
        private static final UUID ASSIGNMENT_TWO_ID = UUID.fromString("10000000-0000-0000-0000-000000000002");
        private static final UUID ASSIGNMENT_THREE_ID = UUID.fromString("10000000-0000-0000-0000-000000000003");
        private static final UUID ASSIGNMENT_FOUR_ID = UUID.fromString("10000000-0000-0000-0000-000000000004");
        private static final UUID ASSIGNMENT_FIVE_ID = UUID.fromString("10000000-0000-0000-0000-000000000005");
        private static final UUID ASSIGNMENT_SIX_ID = UUID.fromString("10000000-0000-0000-0000-000000000006");
        private static final Instant FIXED_INSTANT = Instant.parse("2026-08-02T00:00:00Z");
        private static final LocalDate FIXED_DATE = LocalDate.of(2026, 8, 2);

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
                studentId = STUDENT_ID;
                student = new UserAccount();
                student.setId(studentId);
        }

        @Test
        void UTCID01_getMyProgress_returnsZeroSummaryWhenEnrollmentsAreEmpty() {
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
        void UTCID02_getMyProgress_classifiesOnlineCourseWithOneCompletedVideoAs100Percent() {
                UUID courseId = ONLINE_COURSE_ID;
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
        void UTCID03_getMyProgress_separatesOnline100PercentAndClass50Percent() {
                UUID onlineCourseId = ONLINE_COURSE_ID;
                UUID classCourseId = CLASS_COURSE_ID;
                UUID classId = CLASS_ID;
                CurriculumLesson onlineVideo = lesson(
                                LessonType.VIDEO,
                                LessonStatus.PUBLISHED);
                CurriculumVersion onlineVersion = version(onlineCourseId, onlineVideo);
                CurriculumLesson classVideo1 = lesson(
                                LessonType.VIDEO,
                                LessonStatus.PUBLISHED);
                CurriculumLesson classVideo2 = lesson(
                                SECOND_LESSON_ID,
                                SECOND_LESSON_IDENTITY_ID,
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

        @Test
        void UTCID01_calculateStudentClassProgressPercent_returnsZeroForNoPublishedContent() {
                UUID courseId = CLASS_COURSE_ID;
                UUID classId = CLASS_ID;
                CurriculumVersion version = version(courseId);
                when(curriculumResolutionService.resolveTraineeProgress(
                                courseId, classId, studentId))
                                .thenReturn(resolution(version, classId));
                when(lessonProgressRepository.findByStudentIdAndClassIdAndCourseId(
                                studentId, classId, courseId))
                                .thenReturn(List.of());

                int result = service.calculateStudentClassProgressPercent(
                                studentId, courseId, classId);

                assertThat(result).isZero();
        }

        @Test
        void UTCID02_calculateStudentClassProgressPercent_returns75ForVideoAndFlashcardCompletedQuizIncomplete() {
                UUID courseId = CLASS_COURSE_ID;
                UUID classId = CLASS_ID;
                CurriculumLesson lesson = lesson(LessonType.VIDEO, LessonStatus.PUBLISHED);
                CurriculumLesson quiz = lesson(
                                SECOND_LESSON_ID,
                                SECOND_LESSON_IDENTITY_ID,
                                LessonType.QUIZ,
                                LessonStatus.PUBLISHED);
                CurriculumLesson flashcard = lesson(
                                THIRD_LESSON_ID,
                                THIRD_LESSON_IDENTITY_ID,
                                LessonType.FLASHCARD,
                                LessonStatus.PUBLISHED);
                CurriculumVersion version = version(courseId, lesson, quiz, flashcard);
                when(curriculumResolutionService.resolveTraineeProgress(
                                courseId, classId, studentId))
                                .thenReturn(resolution(version, classId));
                when(lessonProgressRepository.findByStudentIdAndClassIdAndCourseId(
                                studentId, classId, courseId))
                                .thenReturn(List.of(
                                                completedProgress(lesson),
                                                completedProgress(flashcard)));

                int result = service.calculateStudentClassProgressPercent(
                                studentId, courseId, classId);

                assertThat(result).isEqualTo(75);
        }

        @Test
        void UTCID03_calculateStudentClassProgressPercent_ignoresDraftVideoAndReturns100ForPublishedVideo() {
                UUID courseId = CLASS_COURSE_ID;
                UUID classId = CLASS_ID;
                CurriculumLesson published = lesson(LessonType.VIDEO, LessonStatus.PUBLISHED);
                CurriculumLesson draft = lesson(
                                SECOND_LESSON_ID,
                                SECOND_LESSON_IDENTITY_ID,
                                LessonType.VIDEO,
                                LessonStatus.DRAFT);
                CurriculumVersion version = version(courseId, published, draft);
                when(curriculumResolutionService.resolveTraineeProgress(
                                courseId, classId, studentId))
                                .thenReturn(resolution(version, classId));
                when(lessonProgressRepository.findByStudentIdAndClassIdAndCourseId(
                                studentId, classId, courseId))
                                .thenReturn(List.of(completedProgress(published)));

                int result = service.calculateStudentClassProgressPercent(
                                studentId, courseId, classId);

                assertThat(result).isEqualTo(100);
        }

        @Test
        void UTCID01_updateLessonProgress_createsCompletedOnlineVideoProgress() {
                UUID courseId = ONLINE_COURSE_ID;
                CurriculumLesson lesson = lesson(LessonType.VIDEO, LessonStatus.PUBLISHED);
                CurriculumVersion version = version(courseId, lesson);
                when(currentUserService.requireAuthenticatedUser()).thenReturn(student);
                when(curriculumResolutionService.resolveOnlineLearning(courseId, studentId))
                                .thenReturn(resolution(version, null));
                when(curriculumLessonRepository.findEffectiveLessonReference(
                                version.getId(), lesson.getLessonIdentityId()))
                                .thenReturn(Optional.of(lesson));
                when(lessonProgressRepository
                                .findByStudentIdAndCourseIdAndClassIdIsNullAndLessonIdentityId(
                                                studentId, courseId, lesson.getLessonIdentityId()))
                                .thenReturn(Optional.empty());
                when(lessonProgressRepository.save(any(LessonProgress.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                LessonProgressResponse result = service.updateLessonProgress(
                                lesson.getLessonIdentityId(), courseId, null, true);

                assertThat(result.lessonId()).isEqualTo(lesson.getId());
                assertThat(result.courseId()).isEqualTo(courseId);
                assertThat(result.classId()).isNull();
                assertThat(result.lessonIdentityId()).isEqualTo(lesson.getLessonIdentityId());
                assertThat(result.completed()).isTrue();
                assertThat(result.completedAt()).isNotNull();
        }

        @Test
        void UTCID02_updateLessonProgress_rejectsRequestedCourseDifferentFromClassCourse() {
                UUID actualCourseId = ACTUAL_COURSE_ID;
                UUID requestedCourseId = REQUESTED_COURSE_ID;
                UUID classId = CLASS_ID;
                ClassOffering classOffering = new ClassOffering();
                classOffering.setId(classId);
                classOffering.setCourseId(actualCourseId);
                when(currentUserService.requireAuthenticatedUser()).thenReturn(student);
                when(classOfferingRepository.findByIdAndDeletedAtIsNull(classId))
                                .thenReturn(Optional.of(classOffering));

                assertThatThrownBy(() -> service.updateLessonProgress(
                                LESSON_IDENTITY_ID, requestedCourseId, classId, true))
                                .isInstanceOfSatisfying(BusinessException.class, error -> {
                                        assertThat(error.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST);
                                        assertThat(error.getMessage())
                                                        .contains("Class does not belong to the selected course");
                                });
        }

        @Test
        void UTCID03_updateLessonProgress_changesExistingOnlineProgressToIncomplete() {
                UUID courseId = ONLINE_COURSE_ID;
                CurriculumLesson lesson = lesson(LessonType.VIDEO, LessonStatus.PUBLISHED);
                CurriculumVersion version = version(courseId, lesson);
                LessonProgress existing = completedProgress(lesson);
                existing.setCourseId(courseId);
                when(currentUserService.requireAuthenticatedUser()).thenReturn(student);
                when(curriculumResolutionService.resolveOnlineLearning(courseId, studentId))
                                .thenReturn(resolution(version, null));
                when(curriculumLessonRepository.findEffectiveLessonReference(
                                version.getId(), lesson.getLessonIdentityId()))
                                .thenReturn(Optional.of(lesson));
                when(lessonProgressRepository
                                .findByStudentIdAndCourseIdAndClassIdIsNullAndLessonIdentityId(
                                                studentId, courseId, lesson.getLessonIdentityId()))
                                .thenReturn(Optional.of(existing));
                when(lessonProgressRepository.save(existing)).thenReturn(existing);

                LessonProgressResponse result = service.updateLessonProgress(
                                lesson.getLessonIdentityId(), courseId, null, false);

                assertThat(result.completed()).isFalse();
                assertThat(result.completedAt()).isNull();
                assertThat(result.lastAccessedAt()).isNotNull();
        }

        @Test
        void UTCID04_getMyProgress_countsSubmittedGradedLateExpiredAsFourOfSixAssignments() {
                UUID courseId = CLASS_COURSE_ID;
                UUID classId = CLASS_ID;
                CurriculumLesson video = lesson(LessonType.VIDEO, LessonStatus.PUBLISHED);
                CurriculumVersion version = version(courseId, video);
                LessonProgress completedVideo = completedProgress(video);
                List<Assignment> assignments = List.of(
                                assignment(ASSIGNMENT_ONE_ID),
                                assignment(ASSIGNMENT_TWO_ID),
                                assignment(ASSIGNMENT_THREE_ID),
                                assignment(ASSIGNMENT_FOUR_ID),
                                assignment(ASSIGNMENT_FIVE_ID),
                                assignment(ASSIGNMENT_SIX_ID));

                when(currentUserService.requireAuthenticatedUser()).thenReturn(student);
                when(courseEnrollmentService.getMyCourses())
                                .thenReturn(List.of(classCourse(courseId, classId, "Java Class A")));
                when(curriculumResolutionService.resolveTraineeProgress(courseId, classId, studentId))
                                .thenReturn(resolution(version, classId));
                when(lessonProgressRepository.findByStudentIdAndClassIdAndCourseId(
                                studentId, classId, courseId))
                                .thenReturn(List.of(completedVideo));
                when(assignmentRepository.findAvailableForStudent(studentId, courseId, classId, false))
                                .thenReturn(assignments);
                when(assignmentSubmissionRepository.findByAssignmentIdAndStudentId(assignments.get(0).getId(),
                                studentId))
                                .thenReturn(Optional.of(submission(SubmissionStatus.SUBMITTED)));
                when(assignmentSubmissionRepository.findByAssignmentIdAndStudentId(assignments.get(1).getId(),
                                studentId))
                                .thenReturn(Optional.of(submission(SubmissionStatus.GRADED)));
                when(assignmentSubmissionRepository.findByAssignmentIdAndStudentId(assignments.get(2).getId(),
                                studentId))
                                .thenReturn(Optional.of(submission(SubmissionStatus.LATE)));
                when(assignmentSubmissionRepository.findByAssignmentIdAndStudentId(assignments.get(3).getId(),
                                studentId))
                                .thenReturn(Optional.of(submission(SubmissionStatus.EXPIRED)));
                when(assignmentSubmissionRepository.findByAssignmentIdAndStudentId(assignments.get(4).getId(),
                                studentId))
                                .thenReturn(Optional.of(submission(SubmissionStatus.DOING)));
                when(assignmentSubmissionRepository.findByAssignmentIdAndStudentId(assignments.get(5).getId(),
                                studentId))
                                .thenReturn(Optional.empty());

                CourseProgressItemResponse item = service.getMyProgress().courses().get(0);

                assertThat(item.assignment().completed()).isEqualTo(4);
                assertThat(item.assignment().total()).isEqualTo(6);
                assertThat(item.assignment().percent()).isEqualTo(67);
                assertThat(item.courseStatus()).isEqualTo("COMPLETED");
        }

        @Test
        void UTCID04_calculateStudentClassProgressPercent_returnsZeroForNullTypeIncompleteAndNullIdentityRows() {
                UUID courseId = CLASS_COURSE_ID;
                UUID classId = CLASS_ID;
                CurriculumLesson video = lesson(LessonType.VIDEO, LessonStatus.PUBLISHED);
                CurriculumLesson nullType = lesson(LessonType.VIDEO, LessonStatus.PUBLISHED);
                nullType.setId(SECOND_LESSON_ID);
                nullType.setLessonIdentityId(SECOND_LESSON_IDENTITY_ID);
                nullType.setType(null);
                CurriculumVersion version = version(courseId, video, nullType);
                LessonProgress incomplete = completedProgress(video);
                incomplete.setCompleted(false);
                LessonProgress noIdentity = completedProgress(video);
                noIdentity.setLessonIdentityId(null);

                when(curriculumResolutionService.resolveTraineeProgress(courseId, classId, studentId))
                                .thenReturn(resolution(version, classId));
                when(lessonProgressRepository.findByStudentIdAndClassIdAndCourseId(
                                studentId, classId, courseId))
                                .thenReturn(List.of(incomplete, noIdentity));

                int result = service.calculateStudentClassProgressPercent(studentId, courseId, classId);

                assertThat(result).isZero();
        }

        @Test
        void UTCID04_updateLessonProgress_rejectsNullCourseIdWhenClassIdIsNull() {
                when(currentUserService.requireAuthenticatedUser()).thenReturn(student);

                assertThatThrownBy(() -> service.updateLessonProgress(
                                LESSON_IDENTITY_ID, null, null, true))
                                .isInstanceOfSatisfying(BusinessException.class, error -> {
                                        assertThat(error.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST);
                                        assertThat(error.getMessage())
                                                        .isEqualTo("Course is required for online lesson progress");
                                });

                verify(curriculumResolutionService, never()).resolveOnlineLearning(any(), any());
        }

        @Test
        void UTCID05_updateLessonProgress_rejectsClassIdNotFound() {
                UUID classId = CLASS_ID;
                when(currentUserService.requireAuthenticatedUser()).thenReturn(student);
                when(classOfferingRepository.findByIdAndDeletedAtIsNull(classId))
                                .thenReturn(Optional.empty());

                assertThatThrownBy(() -> service.updateLessonProgress(
                                LESSON_IDENTITY_ID, null, classId, true))
                                .isInstanceOfSatisfying(BusinessException.class, error -> {
                                        assertThat(error.errorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
                                        assertThat(error.getMessage()).isEqualTo("Class not found");
                                });
        }

        @Test
        void UTCID06_updateLessonProgress_rejectsLessonIdentityOutsideEffectiveCurriculum() {
                UUID courseId = ONLINE_COURSE_ID;
                UUID lessonIdentityId = LESSON_IDENTITY_ID;
                CurriculumVersion version = version(courseId);
                when(currentUserService.requireAuthenticatedUser()).thenReturn(student);
                when(curriculumResolutionService.resolveOnlineLearning(courseId, studentId))
                                .thenReturn(resolution(version, null));
                when(curriculumLessonRepository.findEffectiveLessonReference(
                                version.getId(), lessonIdentityId))
                                .thenReturn(Optional.empty());

                assertThatThrownBy(() -> service.updateLessonProgress(
                                lessonIdentityId, courseId, null, true))
                                .isInstanceOfSatisfying(BusinessException.class, error -> {
                                        assertThat(error.errorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
                                        assertThat(error.getMessage())
                                                        .isEqualTo("Lesson not found in the effective curriculum");
                                });
        }

        @Test
        void UTCID07_updateLessonProgress_createsCompletedClassPdfProgressUsingResolvedCourse() {
                UUID courseId = CLASS_COURSE_ID;
                UUID classId = CLASS_ID;
                CurriculumLesson lesson = lesson(LessonType.PDF, LessonStatus.PUBLISHED);
                CurriculumVersion version = version(courseId, lesson);
                ClassOffering classOffering = new ClassOffering();
                classOffering.setId(classId);
                classOffering.setCourseId(courseId);

                when(currentUserService.requireAuthenticatedUser()).thenReturn(student);
                when(classOfferingRepository.findByIdAndDeletedAtIsNull(classId))
                                .thenReturn(Optional.of(classOffering));
                when(curriculumResolutionService.resolveTraineeLearning(courseId, classId, studentId))
                                .thenReturn(resolution(version, classId));
                when(curriculumLessonRepository.findEffectiveLessonReference(
                                version.getId(), lesson.getLessonIdentityId()))
                                .thenReturn(Optional.of(lesson));
                when(lessonProgressRepository.findByStudentIdAndClassIdAndLessonIdentityId(
                                studentId, classId, lesson.getLessonIdentityId()))
                                .thenReturn(Optional.empty());
                when(lessonProgressRepository.save(any(LessonProgress.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                LessonProgressResponse result = service.updateLessonProgress(
                                lesson.getLessonIdentityId(), null, classId, true);

                assertThat(result.courseId()).isEqualTo(courseId);
                assertThat(result.classId()).isEqualTo(classId);
                assertThat(result.lessonIdentityId()).isEqualTo(lesson.getLessonIdentityId());
                assertThat(result.completed()).isTrue();
                verify(curriculumResolutionService).resolveTraineeLearning(courseId, classId, studentId);
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
                                ENROLLMENT_ID,
                                "ACTIVE",
                                FIXED_INSTANT,
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
                                FIXED_DATE.plusDays(1),
                                FIXED_DATE.plusMonths(1),
                                30,
                                1L,
                                TRAINER_ID);

                return new MyCourseResponse(
                                courseId,
                                "Class-based Java",
                                "class-based-java",
                                null,
                                BigDecimal.ZERO,
                                null,
                                false,
                                null,
                                ENROLLMENT_ID,
                                "ACTIVE",
                                FIXED_INSTANT,
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
                version.setId(VERSION_ID);
                version.setCourseId(courseId);
                version.setScope(CurriculumScope.MASTER);
                version.setStatus(CurriculumStatus.PUBLISHED);
                version.setVersionNumber(1);

                CurriculumSection section = new CurriculumSection();
                section.setId(SECTION_ID);
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
                return lesson(
                                PRIMARY_LESSON_ID,
                                PRIMARY_LESSON_IDENTITY_ID,
                                type,
                                status);
        }

        private CurriculumLesson lesson(
                        UUID lessonId,
                        UUID lessonIdentityId,
                        LessonType type,
                        LessonStatus status) {
                CurriculumLesson lesson = new CurriculumLesson();
                lesson.setId(lessonId);
                lesson.setLessonIdentityId(lessonIdentityId);
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
                progress.setCompletedAt(FIXED_INSTANT);
                return progress;
        }

        private Assignment assignment(UUID assignmentId) {
                Assignment assignment = new Assignment();
                assignment.setId(assignmentId);
                return assignment;
        }

        private AssignmentSubmission submission(SubmissionStatus status) {
                AssignmentSubmission submission = new AssignmentSubmission();
                submission.setStatus(status);
                return submission;
        }

}