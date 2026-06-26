package com.smartlearnly.backend.classroom.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public interface CoursePublicProjection {
    UUID getId();

    UUID getCourseId();

    String getClassName();

    UUID getTrainerId();

    String getTrainerName();

    String getScheduleDescription();

    LocalDate getStartDate();

    LocalDate getEndDate();

    Integer getMaxStudents();

    BigDecimal getPrice();

    Long getActiveEnrollmentCount();

    String getStatus();
}