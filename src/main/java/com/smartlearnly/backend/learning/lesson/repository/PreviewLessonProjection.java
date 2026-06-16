package com.smartlearnly.backend.learning.lesson.repository;

import java.util.UUID;

public interface PreviewLessonProjection {
    UUID getCourseId();

    UUID getSectionId();

    UUID getLessonId();

    String getTitle();

    String getLessonType();

    String getVideoUrl();

    String getContent();

    String getAttachmentUrl();

    Integer getDurationSeconds();

    Integer getSectionSortOrder();

    Integer getLessonSortOrder();
}
