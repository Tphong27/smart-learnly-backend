package com.smartlearnly.backend.classroom.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.classroom.analytics.dto.ClassAnalyticsResponse;
import com.smartlearnly.backend.classroom.analytics.dto.StudentPerformanceQuery;
import com.smartlearnly.backend.classroom.analytics.repository.ClassAnalyticsRepository;
import com.smartlearnly.backend.classroom.dto.ClassResponse;
import com.smartlearnly.backend.classroom.admin.service.ClassAdminService;
import com.smartlearnly.backend.classroom.trainer.service.ClassTrainerService;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.lessonprogress.trainee.service.TraineeProgressService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KhiemClassAnalyticsServiceReportTest {

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

    @Test
    void UTCID_KHIEM_BE_494_getForAdminOrTmo_buildsUnfilteredSummaryAndPage() {
        ClassResponse classInfo = classInfo();
        UUID firstStudent = UUID.randomUUID();
        UUID secondStudent = UUID.randomUUID();
        Instant oldActivity = Instant.now().minus(40, ChronoUnit.DAYS);
        Instant recentActivity = Instant.now().minus(2, ChronoUnit.DAYS);
        when(classAdminService.get(classInfo.id())).thenReturn(classInfo);
        when(classAnalyticsRepository.findActiveStudents(classInfo.id())).thenReturn(List.of(
                new ClassAnalyticsRepository.StudentBaseRow(
                        firstStudent, "An", "an@example.com", oldActivity),
                new ClassAnalyticsRepository.StudentBaseRow(
                        secondStudent, "Binh", "binh@example.com", recentActivity)));
        when(classAnalyticsRepository.findStudentAssessmentStatistics(classInfo.id()))
                .thenReturn(List.of(
                        new ClassAnalyticsRepository.StudentAssessmentRow(
                                firstStudent, oldActivity, new BigDecimal("7.0"),
                                new BigDecimal("8.0"), true),
                        new ClassAnalyticsRepository.StudentAssessmentRow(
                                secondStudent, recentActivity, new BigDecimal("9.0"),
                                new BigDecimal("9.0"), false)));
        when(traineeProgressService.calculateStudentClassProgressPercent(
                firstStudent, classInfo.courseId(), classInfo.id())).thenReturn(25);
        when(traineeProgressService.calculateStudentClassProgressPercent(
                secondStudent, classInfo.courseId(), classInfo.id())).thenReturn(100);
        when(classAnalyticsRepository.getTestStatistics(classInfo.id()))
                .thenReturn(new ClassAnalyticsRepository.TestStatistics(new BigDecimal("8.0")));
        when(classAnalyticsRepository.getAssignmentStatistics(classInfo.id(), 2))
                .thenReturn(new ClassAnalyticsRepository.AssignmentStatistics(
                        2, 3, new BigDecimal("75.00"), new BigDecimal("8.5"), 1, 1));

        ClassAnalyticsResponse result = service.getForAdminOrTmo(
                classInfo.id(),
                30,
                new StudentPerformanceQuery(null, "all", "all", 0, 20));

        assertThat(result.enrolledStudents()).isEqualTo(2);
        assertThat(result.averageProgress()).isEqualByComparingTo("62.5");
        assertThat(result.inactiveStudents()).isEqualTo(1);
        assertThat(result.assignmentSubmissionRate()).isEqualByComparingTo("75.00");
        assertThat(result.students().items()).hasSize(2);
        assertThat(result.students().items().get(0).studentId()).isEqualTo(firstStudent);
        verify(classTrainerService, never()).getMyAssignedClassDetail(classInfo.id());
    }

    @Test
    void UTCID_KHIEM_BE_495_getForAdminOrTmo_rejectsUnsupportedProgressFilter() {
        ClassResponse classInfo = classInfo();
        when(classAdminService.get(classInfo.id())).thenReturn(classInfo);
        stubEmptyStatistics(classInfo.id());

        assertThatThrownBy(() -> service.getForAdminOrTmo(
                classInfo.id(),
                30,
                new StudentPerformanceQuery(null, "almost_done", "all", 0, 20)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
    }

    @Test
    void UTCID_KHIEM_BE_496_getForTrainer_usesAssignedClassAuthorizationPath() {
        ClassResponse classInfo = classInfo();
        when(classTrainerService.getMyAssignedClassDetail(classInfo.id())).thenReturn(classInfo);
        stubEmptyStatistics(classInfo.id());

        ClassAnalyticsResponse result = service.getForTrainer(
                classInfo.id(),
                14,
                new StudentPerformanceQuery("", "", "", -1, 500));

        assertThat(result.classId()).isEqualTo(classInfo.id());
        assertThat(result.students().page()).isZero();
        assertThat(result.students().size()).isEqualTo(100);
        assertThat(result.averageProgress()).isEqualByComparingTo(BigDecimal.ZERO);
        verify(classAdminService, never()).get(classInfo.id());
    }

    private void stubEmptyStatistics(UUID classId) {
        when(classAnalyticsRepository.findActiveStudents(classId)).thenReturn(List.of());
        when(classAnalyticsRepository.findStudentAssessmentStatistics(classId)).thenReturn(List.of());
        when(classAnalyticsRepository.getTestStatistics(classId))
                .thenReturn(new ClassAnalyticsRepository.TestStatistics(null));
        when(classAnalyticsRepository.getAssignmentStatistics(classId, 0))
                .thenReturn(new ClassAnalyticsRepository.AssignmentStatistics(
                        0, 0, BigDecimal.ZERO, null, 0, 0));
    }

    private ClassResponse classInfo() {
        return new ClassResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Java Backend",
                "Java Backend Class",
                UUID.randomUUID(),
                "Trainer",
                "https://meet.google.com/abc-defg-hij",
                "[]",
                BigDecimal.ZERO,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 9, 1),
                30,
                2,
                28,
                "upcoming",
                Instant.now(),
                Instant.now());
    }
}
