package com.smartlearnly.backend.enrollment.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.time.LocalDate;

public interface MyCourseProjection {
    UUID getId();
    String getTitle();
    String getSlug();
    String getDescription();
    BigDecimal getPrice();
    String getAvatarUrl();
    Boolean getFeatured();

    UUID getCategoryId();
    String getCategoryName();
    String getCategorySlug();

    UUID getEnrollmentId();
    String getEnrollmentStatus();
    Instant getEnrollmentDate();

    String getCourseStatus();
    Instant getAccessBlockedAt();
    String getAccessBlockReason();

    UUID getClassEnrollmentId();
    UUID getClassId();
    String getClassName();
    String getClassStatus();
    String getClassTrainerName();
    String getClassScheduleDescription();
    LocalDate getClassStartDate();
    LocalDate getClassEndDate();
    Integer getClassMaxStudents();
    Long getClassActiveEnrollmentCount();
}
