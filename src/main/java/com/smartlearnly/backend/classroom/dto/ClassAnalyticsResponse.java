package com.smartlearnly.backend.classroom.dto;

import com.smartlearnly.backend.common.api.PageResponse;
import java.math.BigDecimal;
import java.util.UUID;

public record ClassAnalyticsResponse(
        UUID classId,
        String className,
        UUID courseId,
        String courseTitle,
        UUID trainerId,
        String trainerName,
        String status,

        long enrolledStudents,
        int maxStudents,

        BigDecimal averageProgress,
        BigDecimal averageTestScore,
        BigDecimal assignmentSubmissionRate,

        long lateSubmissions,
        long inactiveStudents,
        long pendingGrading,

        PageResponse<StudentPerformanceResponse> students
) {
}