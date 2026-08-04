package com.smartlearnly.backend.classroom.schedule.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record ScheduleSessionResponse(
        UUID sessionId,
        UUID classId,
        UUID courseId,
        String courseTitle,
        String className,
        LocalDate sessionDate,
        LocalTime startTime,
        LocalTime endTime,
        UUID trainerId,
        String trainerName,
        String meetingUrl
) {
}
