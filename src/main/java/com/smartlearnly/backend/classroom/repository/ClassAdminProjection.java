package com.smartlearnly.backend.classroom.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public interface ClassAdminProjection {
    UUID getId();
    UUID getCourseId();
    String getCourseTitle();
    String getClassName();
    UUID getTrainerId();
    String getTrainerName();
    String getScheduleDescription();
    LocalDate getStartDate();
    LocalDate getEndDate();
    Integer getMaxStudents();
    Long getActiveEnrollmentCount();
    String getStatus();
    Instant getCreatedAt();
    Instant getUpdatedAt();
}
