package com.smartlearnly.backend.classroom.service;

import com.smartlearnly.backend.classroom.dto.ClassAnalyticsResponse;
import com.smartlearnly.backend.classroom.dto.ClassResponse;
import com.smartlearnly.backend.classroom.dto.StudentPerformanceQuery;
import com.smartlearnly.backend.classroom.dto.StudentPerformanceResponse;
import com.smartlearnly.backend.classroom.repository.ClassAnalyticsRepository;
import com.smartlearnly.backend.common.api.PageResponse;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.lessonprogress.service.TraineeProgressService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ClassAnalyticsService {

        private static final int MAX_PAGE_SIZE = 100;
        private static final Set<String> PROGRESS_FILTERS = Set.of(
                        "all",
                        "not_started",
                        "in_progress",
                        "completed");

        private static final Set<String> INDICATOR_FILTERS = Set.of(
                        "all",
                        "inactive",
                        "late_submission",
                        "no_alert");

        private final ClassAdminService classAdminService;
        private final ClassTrainerService classTrainerService;
        private final ClassAnalyticsRepository classAnalyticsRepository;
        private final TraineeProgressService traineeProgressService;

        @Transactional(readOnly = true)
        public ClassAnalyticsResponse getForAdminOrTmo(UUID classId, int inactiveDays, StudentPerformanceQuery query) {
                ClassResponse classInfo = classAdminService.get(classId);

                return buildResponse(classInfo, inactiveDays, query);
        }

        @Transactional(readOnly = true)
        public ClassAnalyticsResponse getForTrainer(UUID classId, int inactiveDays, StudentPerformanceQuery query) {
                ClassResponse classInfo = classTrainerService.getMyAssignedClassDetail(classId);

                return buildResponse(classInfo, inactiveDays, query);
        }

        private ClassAnalyticsResponse buildResponse(ClassResponse classInfo, int inactiveDays,
                        StudentPerformanceQuery query) {
                UUID classId = classInfo.id();
                UUID courseId = classInfo.courseId();

                List<ClassAnalyticsRepository.StudentBaseRow> activeStudents = classAnalyticsRepository
                                .findActiveStudents(classId);

                List<ClassAnalyticsRepository.StudentAssessmentRow> assessmentRows = classAnalyticsRepository
                                .findStudentAssessmentStatistics(classId);

                Map<UUID, ClassAnalyticsRepository.StudentAssessmentRow> assessmentByStudent = new HashMap<>();

                for (ClassAnalyticsRepository.StudentAssessmentRow row : assessmentRows) {
                        assessmentByStudent.put(row.studentId(), row);
                }

                Instant inactiveThreshold = Instant.now().minus(inactiveDays, ChronoUnit.DAYS);

                /*
                 * Danh sách đầy đủ dùng để tính các metric tổng.
                 * Filter không được làm thay đổi summary của class.
                 */
                List<StudentPerformanceResponse> allStudents = activeStudents.stream()
                                .map(student -> buildStudentPerformance(
                                                student,
                                                assessmentByStudent.get(student.studentId()),
                                                courseId,
                                                classId,
                                                inactiveThreshold))
                                .toList();

                BigDecimal averageProgress = calculateAverageProgress(allStudents);

                long inactiveStudents = allStudents.stream()
                                .filter(StudentPerformanceResponse::inactive)
                                .count();

                ClassAnalyticsRepository.TestStatistics testStatistics = classAnalyticsRepository
                                .getTestStatistics(classId);

                ClassAnalyticsRepository.AssignmentStatistics assignmentStatistics = classAnalyticsRepository
                                .getAssignmentStatistics(classId, activeStudents.size());

                PageResponse<StudentPerformanceResponse> studentPage = filterAndPaginateStudents(allStudents, query);

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

                                studentPage);
        }

        private StudentPerformanceResponse buildStudentPerformance(
                        ClassAnalyticsRepository.StudentBaseRow student,
                        ClassAnalyticsRepository.StudentAssessmentRow assessment,
                        UUID courseId,
                        UUID classId,
                        Instant inactiveThreshold) {
                int progressPercent = traineeProgressService
                                .calculateStudentClassProgressPercent(student.studentId(), courseId, classId);

                Instant lastLearningActivity = assessment == null ? null : assessment.lastActivityAt();

                Instant effectiveActivity = latest(student.enrollmentDate(), lastLearningActivity);

                boolean inactive = effectiveActivity != null && effectiveActivity.isBefore(inactiveThreshold);

                return new StudentPerformanceResponse(
                                student.studentId(),
                                student.studentName(),
                                student.email(),
                                progressPercent,
                                assessment == null ? null : assessment.averageTestScore(),
                                assessment == null ? null : assessment.averageAssignmentScore(),
                                lastLearningActivity,
                                inactive,
                                assessment != null && assessment.hasLateSubmission());
        }

        private PageResponse<StudentPerformanceResponse> filterAndPaginateStudents(
                        List<StudentPerformanceResponse> students,
                        StudentPerformanceQuery query) {
                String keyword = normalizeText(query.keyword());

                String progress = normalizeFilter(
                                query.progress(),
                                PROGRESS_FILTERS,
                                "Progress filter must be all, "
                                                + "not_started, in_progress, "
                                                + "or completed");

                String indicator = normalizeFilter(
                                query.indicator(),
                                INDICATOR_FILTERS,
                                "Indicator filter must be all, "
                                                + "inactive, late_submission, "
                                                + "or no_alert");

                int requestedPage = Math.max(0, query.page());

                int requestedSize = Math.min(
                                Math.max(1, query.size()),
                                MAX_PAGE_SIZE);

                List<StudentPerformanceResponse> filteredStudents = students.stream()
                                .filter(student -> matchesKeyword(student, keyword))
                                .filter(student -> matchesProgress(student, progress))
                                .filter(student -> matchesIndicator(student, indicator))
                                .sorted(this::compareStudents)
                                .toList();

                long totalItems = filteredStudents.size();

                int totalPages = totalItems == 0 ? 0 : (int) Math.ceil((double) totalItems / requestedSize);

                long requestedOffset = (long) requestedPage * requestedSize;

                int fromIndex = (int) Math.min(requestedOffset, totalItems);

                int toIndex = Math.min(fromIndex + requestedSize, filteredStudents.size());

                List<StudentPerformanceResponse> pageItems = List.copyOf(filteredStudents.subList(fromIndex, toIndex));

                return new PageResponse<>(
                                pageItems,
                                requestedPage,
                                requestedSize,
                                totalItems,
                                totalPages);
        }

        private boolean matchesKeyword(StudentPerformanceResponse student, String keyword) {
                if (keyword.isBlank()) {
                        return true;
                }

                return normalizeText(
                                student.studentName()).contains(keyword)
                                || normalizeText(student.email()).contains(keyword);
        }

        private boolean matchesProgress(
                        StudentPerformanceResponse student,
                        String progress) {
                int progressPercent = student.progressPercent();

                return switch (progress) {
                        case "not_started" ->
                                progressPercent <= 0;

                        case "in_progress" ->
                                progressPercent > 0&& progressPercent < 100;

                        case "completed" ->
                                progressPercent >= 100;

                        default -> true;
                };
        }

        private boolean matchesIndicator(
                        StudentPerformanceResponse student,
                        String indicator) {
                return switch (indicator) {
                        case "inactive" ->
                                student.inactive();

                        case "late_submission" ->
                                student.hasLateSubmission();

                        case "no_alert" ->
                                !student.inactive() && !student.hasLateSubmission();

                        default -> true;
                };
        }

        private int compareStudents(StudentPerformanceResponse first, StudentPerformanceResponse second) {
                if (first.inactive() != second.inactive()) {
                        return first.inactive() ? -1 : 1;
                }

                int progressComparison = Integer.compare(first.progressPercent(), second.progressPercent());

                if (progressComparison != 0) {
                        return progressComparison;
                }

                return String.CASE_INSENSITIVE_ORDER.compare(
                                first.studentName() == null ? "" : first.studentName(),
                                second.studentName() == null ? "" : second.studentName());
        }

        private String normalizeFilter(
                        String value,
                        Set<String> acceptedValues,
                        String errorMessage) {
                String normalized = normalizeText(value);

                if (normalized.isBlank()) {
                        return "all";
                }

                if (!acceptedValues.contains(normalized)) {
                        throw new BusinessException(
                                        ErrorCode.INVALID_REQUEST,
                                        errorMessage);
                }

                return normalized;
        }

        private String normalizeText(String value) {
                if (value == null) {
                        return "";
                }

                return value.trim().toLowerCase(Locale.ROOT);
        }

        private BigDecimal calculateAverageProgress(List<StudentPerformanceResponse> students) {
                if (students.isEmpty()) {
                        return BigDecimal.ZERO;
                }

                double average = students.stream()
                                .mapToInt(StudentPerformanceResponse::progressPercent)
                                .average()
                                .orElse(0);

                return BigDecimal
                                .valueOf(average)
                                .setScale(1, RoundingMode.HALF_UP);
        }

        private Instant latest(Instant first, Instant second) {
                if (first == null) {
                        return second;
                }

                if (second == null) {
                        return first;
                }

                return first.isAfter(second) ? first : second;
        }
}