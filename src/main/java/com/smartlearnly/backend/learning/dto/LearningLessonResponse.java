package com.smartlearnly.backend.learning.dto;

import java.util.List;
import java.util.UUID;

public record LearningLessonResponse(
    UUID lessonId,
    String title,
    String lessonType,
    String status,
    String videoUrl,
    String content,
    String attachmentUrl,
    Integer durationSeconds,
    boolean isPreview,
    int sortOrder,
    boolean completed,
    List<LearningResourceResponse> resources,
    boolean hlsReady,
    String hlsPlaylistUrl,
    UUID lessonIdentityId
) {
    public LearningLessonResponse(
            UUID lessonId,
            String title,
            String lessonType,
            String status,
            String videoUrl,
            String content,
            String attachmentUrl,
            Integer durationSeconds,
            boolean isPreview,
            int sortOrder,
            boolean completed,
            List<LearningResourceResponse> resources,
            boolean hlsReady,
            String hlsPlaylistUrl) {
        this(
                lessonId,
                title,
                lessonType,
                status,
                videoUrl,
                content,
                attachmentUrl,
                durationSeconds,
                isPreview,
                sortOrder,
                completed,
                resources,
                hlsReady,
                hlsPlaylistUrl,
                null);
    }
}
