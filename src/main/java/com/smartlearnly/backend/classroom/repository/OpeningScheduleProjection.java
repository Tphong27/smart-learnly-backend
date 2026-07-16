package com.smartlearnly.backend.classroom.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public interface OpeningScheduleProjection {
    UUID getClassId();
    UUID getCourseId();
    String getCourseTitle();
    String getCourseSlug();
    String getCourseThumbnailUrl();
    String getClassName();
    UUID getTrainerId();
    String getTrainerName();
    LocalDate getStartDate();
    LocalDate getEndDate();
    String getScheduleDescription();
    BigDecimal getPrice();
    Integer getMaxStudents();
    Long getActiveEnrollmentCount();
    String getStatus();
}