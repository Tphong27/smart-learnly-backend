package com.smartlearnly.backend.enrollment.repository;

import java.time.Instant;
import java.util.UUID;

public interface EnrollmentHistoryProjection {
    UUID getEnrollmentId();
    UUID getCourseId();
    String getCourseTitle();
    String getCourseSlug();
    String getStatus();
    Instant getEnrollmentDate();
    Instant getUpdatedAt();
}
