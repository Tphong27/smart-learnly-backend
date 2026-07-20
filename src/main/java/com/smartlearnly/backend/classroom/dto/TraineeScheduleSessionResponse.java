package com.smartlearnly.backend.classroom.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record TraineeScheduleSessionResponse(
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