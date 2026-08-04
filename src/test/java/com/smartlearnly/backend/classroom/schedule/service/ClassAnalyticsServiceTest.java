package com.smartlearnly.backend.classroom.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.classroom.dto.ClassAnalyticsResponse;
import com.smartlearnly.backend.classroom.dto.ClassResponse;
import com.smartlearnly.backend.classroom.dto.StudentPerformanceQuery;
import com.smartlearnly.backend.classroom.repository.ClassAnalyticsRepository;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.lessonprogress.service.TraineeProgressService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClassAnalyticsServiceTest {

        private static final UUID CLASS_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
        private static final UUID COURSE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
        private static final UUID TRAINER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
        private static final UUID STUDENT_AN_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
        private static final UUID STUDENT_BINH_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
        private static final UUID STUDENT_CHI_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");

        private static final Instant OLD_ACTIVITY = Instant.parse("2000-01-01T00:00:00Z");
        private static final Instant RECENT_ACTIVITY = Instant.parse("2099-01-01T00:00:00Z");

        @Mock
        private ClassAdminService classAdminService;

        @Mock
        private ClassTrainerService classTrainerService;

        @Mock
        private ClassAnalyticsRepository classAnalyticsRepository;

        @Mock
        private TraineeProgressService traineeProgressService;

        private ClassAnalyticsService service;

        @BeforeEach
        void setUp() {
                service = new ClassAnalyticsService(
                                classAdminService,
                                classTrainerService,
                                classAnalyticsRepository,
                                traineeProgressService);
        }

        // getForAdminOrTmo(): UTCID restarts from UTCID01.

        @Test
        void UTCID01_getForAdminOrTmo_buildsSummaryForTwoStudentsWithAllFilters() {
                ClassResponse classInfo = classInfo();
                when(classAdminService.get(CLASS_ID)).thenReturn(classInfo);
                when(classAnalyticsRepository.findActiveStudents(CLASS_ID)).thenReturn(List.of(
                                new ClassAnalyticsRepository.StudentBaseRow(
                                                STUDENT_AN_ID,
                                                "An Nguyen",
                                                "an@example.com",
                                                OLD_ACTIVITY),
                                new ClassAnalyticsRepository.StudentBaseRow(
                                                STUDENT_BINH_ID,
                                                "Binh Nguyen",
                                                "binh@example.com",
                                                RECENT_ACTIVITY)));
                when(classAnalyticsRepository.findStudentAssessmentStatistics(CLASS_ID))
                                .thenReturn(List.of(
                                                new ClassAnalyticsRepository.StudentAssessmentRow(
                                                                STUDENT_AN_ID,
                                                                OLD_ACTIVITY,
                                                                new BigDecimal("7.0"),
                                                                new BigDecimal("8.0"),
                                                                true),
                                                new ClassAnalyticsRepository.StudentAssessmentRow(
                                                                STUDENT_BINH_ID,
                                                                RECENT_ACTIVITY,
                                                                new BigDecimal("9.0"),
                                                                new BigDecimal("9.0"),
                                                                false)));
                when(traineeProgressService.calculateStudentClassProgressPercent(
                                STUDENT_AN_ID,
                                COURSE_ID,
                                CLASS_ID))
                                .thenReturn(25);
                when(traineeProgressService.calculateStudentClassProgressPercent(
                                STUDENT_BINH_ID,
                                COURSE_ID,
                                CLASS_ID))
                                .thenReturn(100);
                when(classAnalyticsRepository.getTestStatistics(CLASS_ID))
                                .thenReturn(new ClassAnalyticsRepository.TestStatistics(
                                                new BigDecimal("8.0")));
                when(classAnalyticsRepository.getAssignmentStatistics(CLASS_ID, 2))
                                .thenReturn(new ClassAnalyticsRepository.AssignmentStatistics(
                                                2,
                                                3,
                                                new BigDecimal("75.00"),
                                                new BigDecimal("8.5"),
                                                1,
                                                1));

                StudentPerformanceQuery query = new StudentPerformanceQuery(null, "all", "all", 0, 20);
                ClassAnalyticsResponse result = service.getForAdminOrTmo(CLASS_ID, 30, query);

                assertThat(result.classId()).isEqualTo(CLASS_ID);
                assertThat(result.enrolledStudents()).isEqualTo(2);
                assertThat(result.averageProgress()).isEqualByComparingTo("62.5");
                assertThat(result.inactiveStudents()).isEqualTo(1);
                assertThat(result.assignmentSubmissionRate()).isEqualByComparingTo("75.00");
                assertThat(result.students().items()).hasSize(2);
                assertThat(result.students().items().get(0).studentId()).isEqualTo(STUDENT_AN_ID);
                verify(classTrainerService, never()).getMyAssignedClassDetail(CLASS_ID);
        }

        @Test
        void UTCID02_getForAdminOrTmo_rejectsProgressFilterAlmostDone() {
                ClassResponse classInfo = classInfo();
                when(classAdminService.get(CLASS_ID)).thenReturn(classInfo);
                stubEmptyStatistics();

                StudentPerformanceQuery query = new StudentPerformanceQuery(null, "almost_done", "all", 0, 20);

                assertThatThrownBy(() -> service.getForAdminOrTmo(CLASS_ID, 30, query))
                                .isInstanceOfSatisfying(BusinessException.class, error -> {
                                        assertThat(error.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST);
                                        assertThat(error.getMessage())
                                                        .isEqualTo(
                                                                        "Progress filter must be all, not_started, in_progress, or completed");
                                });
        }

        @Test
        void UTCID03_getForAdminOrTmo_filtersKeywordProgressAndEveryIndicatorOption() {
                ClassResponse classInfo = classInfo();
                when(classAdminService.get(CLASS_ID)).thenReturn(classInfo);
                when(classAnalyticsRepository.findActiveStudents(CLASS_ID)).thenReturn(List.of(
                                new ClassAnalyticsRepository.StudentBaseRow(
                                                STUDENT_AN_ID,
                                                "An Nguyen",
                                                "an@example.com",
                                                OLD_ACTIVITY),
                                new ClassAnalyticsRepository.StudentBaseRow(
                                                STUDENT_BINH_ID,
                                                "Binh Nguyen",
                                                "binh@example.com",
                                                RECENT_ACTIVITY),
                                new ClassAnalyticsRepository.StudentBaseRow(
                                                STUDENT_CHI_ID,
                                                "Chi Nguyen",
                                                "chi@example.com",
                                                RECENT_ACTIVITY)));
                when(classAnalyticsRepository.findStudentAssessmentStatistics(CLASS_ID))
                                .thenReturn(List.of(
                                                new ClassAnalyticsRepository.StudentAssessmentRow(
                                                                STUDENT_AN_ID, null, null, null, false),
                                                new ClassAnalyticsRepository.StudentAssessmentRow(
                                                                STUDENT_BINH_ID,
                                                                RECENT_ACTIVITY,
                                                                new BigDecimal("7.0"),
                                                                null,
                                                                false),
                                                new ClassAnalyticsRepository.StudentAssessmentRow(
                                                                STUDENT_CHI_ID,
                                                                RECENT_ACTIVITY,
                                                                new BigDecimal("9.0"),
                                                                new BigDecimal("8.0"),
                                                                true)));
                when(traineeProgressService.calculateStudentClassProgressPercent(
                                STUDENT_AN_ID, COURSE_ID, CLASS_ID)).thenReturn(0);
                when(traineeProgressService.calculateStudentClassProgressPercent(
                                STUDENT_BINH_ID, COURSE_ID, CLASS_ID)).thenReturn(50);
                when(traineeProgressService.calculateStudentClassProgressPercent(
                                STUDENT_CHI_ID, COURSE_ID, CLASS_ID)).thenReturn(100);
                stubStatistics(3);

                ClassAnalyticsResponse inactive = service.getForAdminOrTmo(
                                CLASS_ID,
                                30,
                                new StudentPerformanceQuery(
                                                " AN ", "not_started", "inactive", 0, 20));
                ClassAnalyticsResponse noAlert = service.getForAdminOrTmo(
                                CLASS_ID,
                                30,
                                new StudentPerformanceQuery(
                                                "BINH@EXAMPLE.COM", "in_progress", "no_alert", 0, 20));
                ClassAnalyticsResponse late = service.getForAdminOrTmo(
                                CLASS_ID,
                                30,
                                new StudentPerformanceQuery(
                                                null, "completed", "late_submission", 0, 20));

                assertThat(inactive.students().items())
                                .extracting(student -> student.studentId())
                                .containsExactly(STUDENT_AN_ID);
                assertThat(noAlert.students().items())
                                .extracting(student -> student.studentId())
                                .containsExactly(STUDENT_BINH_ID);
                assertThat(late.students().items())
                                .extracting(student -> student.studentId())
                                .containsExactly(STUDENT_CHI_ID);
                assertThat(late.averageProgress()).isEqualByComparingTo("50.0");
        }

        @Test
        void UTCID04_getForAdminOrTmo_sortsNullAlphaBetaAndReturnsEmptyPageThreeSizeTwo() {
                ClassResponse classInfo = classInfo();
                when(classAdminService.get(CLASS_ID)).thenReturn(classInfo);
                when(classAnalyticsRepository.findActiveStudents(CLASS_ID)).thenReturn(List.of(
                                new ClassAnalyticsRepository.StudentBaseRow(
                                                STUDENT_BINH_ID,
                                                "beta",
                                                "b@example.com",
                                                RECENT_ACTIVITY),
                                new ClassAnalyticsRepository.StudentBaseRow(
                                                STUDENT_AN_ID,
                                                "Alpha",
                                                "a@example.com",
                                                RECENT_ACTIVITY),
                                new ClassAnalyticsRepository.StudentBaseRow(
                                                STUDENT_CHI_ID,
                                                null,
                                                null,
                                                null)));
                when(classAnalyticsRepository.findStudentAssessmentStatistics(CLASS_ID))
                                .thenReturn(List.of());
                when(traineeProgressService.calculateStudentClassProgressPercent(
                                STUDENT_BINH_ID, COURSE_ID, CLASS_ID)).thenReturn(50);
                when(traineeProgressService.calculateStudentClassProgressPercent(
                                STUDENT_AN_ID, COURSE_ID, CLASS_ID)).thenReturn(50);
                when(traineeProgressService.calculateStudentClassProgressPercent(
                                STUDENT_CHI_ID, COURSE_ID, CLASS_ID)).thenReturn(50);
                stubStatistics(3);

                ClassAnalyticsResponse firstPage = service.getForAdminOrTmo(
                                CLASS_ID,
                                30,
                                new StudentPerformanceQuery(null, null, null, 0, 2));
                ClassAnalyticsResponse outOfRange = service.getForAdminOrTmo(
                                CLASS_ID,
                                30,
                                new StudentPerformanceQuery(null, "all", "all", 3, 2));

                assertThat(firstPage.students().items())
                                .extracting(student -> student.studentId())
                                .containsExactly(STUDENT_CHI_ID, STUDENT_AN_ID);
                assertThat(firstPage.students().totalItems()).isEqualTo(3);
                assertThat(firstPage.students().totalPages()).isEqualTo(2);
                assertThat(outOfRange.students().items()).isEmpty();
                assertThat(outOfRange.students().page()).isEqualTo(3);
                assertThat(outOfRange.students().size()).isEqualTo(2);
        }

        @Test
        void UTCID05_getForAdminOrTmo_rejectsIndicatorFilterAtRisk() {
                ClassResponse classInfo = classInfo();
                when(classAdminService.get(CLASS_ID)).thenReturn(classInfo);
                stubEmptyStatistics();

                StudentPerformanceQuery query = new StudentPerformanceQuery(null, "all", "at_risk", 0, 20);

                assertThatThrownBy(() -> service.getForAdminOrTmo(CLASS_ID, 30, query))
                                .isInstanceOfSatisfying(BusinessException.class, error -> {
                                        assertThat(error.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST);
                                        assertThat(error.getMessage())
                                                        .isEqualTo(
                                                                        "Indicator filter must be all, inactive, late_submission, or no_alert");
                                });
        }

        // getForTrainer(): UTCID restarts from UTCID01.

        @Test
        void UTCID01_getForTrainer_usesAssignedClassAndClampsPageMinusOneSize500ToPageZeroSize100() {
                ClassResponse classInfo = classInfo();
                when(classTrainerService.getMyAssignedClassDetail(CLASS_ID)).thenReturn(classInfo);
                stubEmptyStatistics();

                StudentPerformanceQuery query = new StudentPerformanceQuery("", "", "", -1, 500);
                ClassAnalyticsResponse result = service.getForTrainer(CLASS_ID, 14, query);

                assertThat(result.classId()).isEqualTo(CLASS_ID);
                assertThat(result.students().page()).isZero();
                assertThat(result.students().size()).isEqualTo(100);
                assertThat(result.averageProgress()).isEqualByComparingTo(BigDecimal.ZERO);
                verify(classAdminService, never()).get(CLASS_ID);
        }

        @Test
        void UTCID02_getForTrainer_propagatesForbiddenWhenTrainerIsNotAssigned() {
                BusinessException forbidden = new BusinessException(
                                ErrorCode.FORBIDDEN,
                                "Trainer is not assigned to this class");
                when(classTrainerService.getMyAssignedClassDetail(CLASS_ID))
                                .thenThrow(forbidden);

                StudentPerformanceQuery query = new StudentPerformanceQuery(null, null, null, 0, 20);

                assertThatThrownBy(() -> service.getForTrainer(CLASS_ID, 14, query))
                                .isSameAs(forbidden);

                verify(classAnalyticsRepository, never()).findActiveStudents(CLASS_ID);
        }

        private void stubEmptyStatistics() {
                when(classAnalyticsRepository.findActiveStudents(CLASS_ID)).thenReturn(List.of());
                when(classAnalyticsRepository.findStudentAssessmentStatistics(CLASS_ID))
                                .thenReturn(List.of());
                when(classAnalyticsRepository.getTestStatistics(CLASS_ID))
                                .thenReturn(new ClassAnalyticsRepository.TestStatistics(null));
                when(classAnalyticsRepository.getAssignmentStatistics(CLASS_ID, 0))
                                .thenReturn(new ClassAnalyticsRepository.AssignmentStatistics(
                                                0, 0, BigDecimal.ZERO, null, 0, 0));
        }

        private void stubStatistics(long activeStudentCount) {
                when(classAnalyticsRepository.getTestStatistics(CLASS_ID))
                                .thenReturn(new ClassAnalyticsRepository.TestStatistics(
                                                new BigDecimal("8.0")));
                when(classAnalyticsRepository.getAssignmentStatistics(
                                CLASS_ID,
                                activeStudentCount))
                                .thenReturn(new ClassAnalyticsRepository.AssignmentStatistics(
                                                2,
                                                3,
                                                new BigDecimal("50.00"),
                                                new BigDecimal("8.0"),
                                                1,
                                                1));
        }

        private ClassResponse classInfo() {
                return new ClassResponse(
                                CLASS_ID,
                                COURSE_ID,
                                "Java Backend",
                                "Java Backend Class",
                                TRAINER_ID,
                                "Trainer Nguyen",
                                "https://meet.google.com/abc-defg-hij",
                                "[]",
                                BigDecimal.ZERO,
                                LocalDate.of(2026, 8, 1),
                                LocalDate.of(2026, 9, 1),
                                30,
                                2,
                                28,
                                "upcoming",
                                Instant.parse("2026-07-01T00:00:00Z"),
                                Instant.parse("2026-07-02T00:00:00Z"));
        }
}