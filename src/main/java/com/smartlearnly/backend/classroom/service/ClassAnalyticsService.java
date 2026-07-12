package com.smartlearnly.backend.classroom.service;

import com.smartlearnly.backend.classroom.dto.ClassAnalyticsResponse;
import com.smartlearnly.backend.classroom.dto.ClassResponse;
import com.smartlearnly.backend.classroom.dto.StudentPerformanceResponse;
import com.smartlearnly.backend.classroom.repository.ClassAnalyticsRepository;
import com.smartlearnly.backend.lessonprogress.service.TraineeProgressService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClassAnalyticsService {

    private final ClassAdminService classAdminService;
    private final ClassTrainerService classTrainerService;
    private final ClassAnalyticsRepository classAnalyticsRepository;
    private final TraineeProgressService traineeProgressService;

    @Transactional(readOnly = true)
    public ClassAnalyticsResponse getForAdminOrTmo(
            UUID classId,
            int inactiveDays) {
        ClassResponse classInfo = classAdminService.get(classId);

        return buildResponse(
                classInfo,
                inactiveDays);
    }

    @Transactional(readOnly = true)
    public ClassAnalyticsResponse getForTrainer(
            UUID classId,
            int inactiveDays) {
        ClassResponse classInfo = classTrainerService
                .getMyAssignedClassDetail(classId);

        return buildResponse(
                classInfo,
                inactiveDays);
    }

    private ClassAnalyticsResponse buildResponse(
            ClassResponse classInfo,
            int inactiveDays) {
        UUID classId = classInfo.id();
        UUID courseId = classInfo.courseId();

        List<ClassAnalyticsRepository.StudentBaseRow> activeStudents = classAnalyticsRepository
                .findActiveStudents(classId);

        List<ClassAnalyticsRepository.StudentAssessmentRow> assessmentRows = classAnalyticsRepository
                .findStudentAssessmentStatistics(
                        classId);

        Map<UUID, ClassAnalyticsRepository.StudentAssessmentRow> assessmentByStudent = new HashMap<>();

        for (ClassAnalyticsRepository.StudentAssessmentRow row : assessmentRows) {
            assessmentByStudent.put(
                    row.studentId(),
                    row);
        }

        Instant inactiveThreshold = Instant.now()
                .minus(
                        inactiveDays,
                        ChronoUnit.DAYS);

        List<StudentPerformanceResponse> students = activeStudents.stream()
                .map(student -> buildStudentPerformance(
                        student,
                        assessmentByStudent
                                .get(
                                        student.studentId()),
                        courseId,
                        classId,
                        inactiveThreshold))
                .toList();

        BigDecimal averageProgress = calculateAverageProgress(students);

        long inactiveStudents = students.stream()
                .filter(
                        StudentPerformanceResponse::inactive)
                .count();

        ClassAnalyticsRepository.TestStatistics testStatistics = classAnalyticsRepository
                .getTestStatistics(classId);

        ClassAnalyticsRepository.AssignmentStatistics assignmentStatistics = classAnalyticsRepository
                .getAssignmentStatistics(
                        classId,
                        activeStudents.size());

        return new ClassAnalyticsResponse(
                classInfo.id(),
                classInfo.className(),
                classInfo.courseId(),
                classInfo.courseTitle(),
                classInfo.trainerId(),
                classInfo.trainerName(),
                classInfo.status(),

                activeStudents.size(),
                classInfo.maxStudents(),

                averageProgress,
                testStatistics.averageScore(),
                assignmentStatistics.submissionRate(),

                assignmentStatistics.lateSubmissions(),
                inactiveStudents,
                assignmentStatistics.pendingGrading(),

                students);
    }

    private StudentPerformanceResponse buildStudentPerformance(
            ClassAnalyticsRepository.StudentBaseRow student,
            ClassAnalyticsRepository.StudentAssessmentRow assessment,
            UUID courseId,
            UUID classId,
            Instant inactiveThreshold) {
        int progressPercent = traineeProgressService
                .calculateStudentClassProgressPercent(
                        student.studentId(),
                        courseId,
                        classId);

        Instant lastLearningActivity = assessment == null
                ? null
                : assessment.lastActivityAt();

        Instant effectiveActivity = latest(
                student.enrollmentDate(),
                lastLearningActivity);

        boolean inactive = effectiveActivity != null
                && effectiveActivity
                        .isBefore(inactiveThreshold);

        return new StudentPerformanceResponse(
                student.studentId(),
                student.studentName(),
                student.email(),

                progressPercent,

                assessment == null
                        ? null
                        : assessment.averageTestScore(),

                assessment == null
                        ? null
                        : assessment.averageAssignmentScore(),

                lastLearningActivity,

                inactive,

                assessment != null
                        && assessment.hasLateSubmission());
    }

    private BigDecimal calculateAverageProgress(
            List<StudentPerformanceResponse> students) {
        if (students.isEmpty()) {
            return BigDecimal.ZERO;
        }

        double average = students.stream()
                .mapToInt(
                        StudentPerformanceResponse::progressPercent)
                .average()
                .orElse(0);

        return BigDecimal
                .valueOf(average)
                .setScale(
                        1,
                        RoundingMode.HALF_UP);
    }

    private Instant latest(
            Instant first,
            Instant second) {
        if (first == null) {
            return second;
        }

        if (second == null) {
            return first;
        }

        return first.isAfter(second)
                ? first
                : second;
    }
}