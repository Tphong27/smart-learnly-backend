package com.smartlearnly.backend.curriculum.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smartlearnly.backend.curriculum.entity.CurriculumLesson;
import com.smartlearnly.backend.learning.dto.LearningLessonResponse;
import com.smartlearnly.backend.learning.lesson.entity.LessonStatus;
import com.smartlearnly.backend.learning.lesson.entity.LessonType;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CurriculumDtoMapperTest {

    private final CurriculumDtoMapper mapper = new CurriculumDtoMapper();

    @Test
    void toLearningLessonResponseIncludesReadyVideoPlaybackState() {
        CurriculumLesson lesson = videoLesson();

        LearningLessonResponse response = mapper.toLearningLessonResponse(
                lesson,
                false,
                Set.of(lesson.getId()));

        assertTrue(response.hlsReady());
        assertEquals(
                "/api/v1/hls/playlist/" + lesson.getId(),
                response.hlsPlaylistUrl());
    }

    @Test
    void toLearningLessonResponseKeepsPlaybackStateEmptyWhenVideoIsNotReady() {
        CurriculumLesson lesson = videoLesson();

        LearningLessonResponse response = mapper.toLearningLessonResponse(
                lesson,
                false,
                Set.of());

        assertFalse(response.hlsReady());
        assertNull(response.hlsPlaylistUrl());
    }

    private CurriculumLesson videoLesson() {
        CurriculumLesson lesson = new CurriculumLesson();
        lesson.setId(UUID.randomUUID());
        lesson.setLessonIdentityId(UUID.randomUUID());
        lesson.setTitle("Video lesson");
        lesson.setType(LessonType.VIDEO);
        lesson.setStatus(LessonStatus.PUBLISHED);
        lesson.setPreview(false);
        lesson.setSortOrder(0);
        return lesson;
    }
}
