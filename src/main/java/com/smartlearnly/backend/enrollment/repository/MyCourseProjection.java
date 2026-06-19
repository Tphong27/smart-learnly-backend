package com.smartlearnly.backend.enrollment.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

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
}
