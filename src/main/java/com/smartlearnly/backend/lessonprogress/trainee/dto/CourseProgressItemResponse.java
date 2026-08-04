package com.smartlearnly.backend.lessonprogress.trainee.dto;

import java.util.UUID;
import java.time.LocalDate;

public record CourseProgressItemResponse(
                UUID id,
                UUID courseId,
                UUID enrollmentId,

                UUID classId,
                UUID classEnrollmentId,
                String className,
                String classMeetingUrl,
                String classScheduleDescription,
                LocalDate classStartDate,
                LocalDate classEndDate,

                String title,
                String categoryName,
                String enrollmentStatus,
                String courseStatus,
                boolean accessAllowed,
                String accessBlockedReason,
                String thumbnailUrl,
                int overallPercent,
                ProgressMetricResponse lesson,
                ProgressMetricResponse quiz,
                ProgressMetricResponse flashcard,
                ProgressMetricResponse assignment) {
}
