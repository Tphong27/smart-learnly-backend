package com.smartlearnly.backend.curriculum.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smartlearnly.backend.curriculum.entity.CurriculumLesson;
import com.smartlearnly.backend.curriculum.entity.CurriculumScope;
import com.smartlearnly.backend.curriculum.entity.CurriculumSection;
import com.smartlearnly.backend.curriculum.entity.CurriculumStatus;
import com.smartlearnly.backend.curriculum.entity.CurriculumVersion;
import com.smartlearnly.backend.learning.dto.LearningContentResponse;
import com.smartlearnly.backend.learning.dto.LearningLessonResponse;
import com.smartlearnly.backend.learning.lesson.entity.LessonStatus;
import com.smartlearnly.backend.learning.lesson.entity.LessonType;
import java.time.Instant;
import java.util.List;
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

    @Test
    void toLearningContentResponseIncludesAllPublishedLessonTypesForTrainee() {
        UUID courseId = UUID.randomUUID();
        CurriculumVersion version = new CurriculumVersion();
        version.setId(UUID.randomUUID());
        version.setCourseId(courseId);
        version.setScope(CurriculumScope.MASTER);
        version.setStatus(CurriculumStatus.PUBLISHED);
        version.setVersionNumber(1);

        CurriculumSection section = new CurriculumSection();
        section.setId(UUID.randomUUID());
        section.setTitle("Complete curriculum");
        section.setSortOrder(0);
        section.setCreatedAt(Instant.now());
        version.addSection(section);

        CurriculumLesson video = publishedLesson(LessonType.VIDEO, 0);
        CurriculumLesson text = publishedLesson(LessonType.RICH_TEXT, 1);
        CurriculumLesson quiz = publishedLesson(LessonType.QUIZ, 2);
        CurriculumLesson flashcard = publishedLesson(LessonType.FLASHCARD, 3);
        CurriculumLesson assignment = publishedLesson(LessonType.ESSAY, 4);
        CurriculumLesson draft = publishedLesson(LessonType.VIDEO, 5);
        draft.setStatus(LessonStatus.DRAFT);
        List.of(video, text, quiz, flashcard, assignment, draft).forEach(section::addLesson);

        LearningContentResponse response = mapper.toLearningContentResponse(
                version,
                "Complete course",
                null,
                Set.of(text.getLessonIdentityId()),
                null,
                Set.of(video.getId()));

        List<LearningLessonResponse> lessons = response.sections().get(0).lessons();
        assertEquals(
                List.of("VIDEO", "RICH_TEXT", "QUIZ", "FLASHCARD", "ESSAY"),
                lessons.stream().map(LearningLessonResponse::lessonType).toList());
        assertEquals(5, response.stats().totalLessons());
        assertTrue(lessons.get(1).completed());
        assertTrue(lessons.get(0).hlsReady());
    }

    private CurriculumLesson videoLesson() {
        return publishedLesson(LessonType.VIDEO, 0);
    }

    private CurriculumLesson publishedLesson(LessonType type, int sortOrder) {
        CurriculumLesson lesson = new CurriculumLesson();
        lesson.setId(UUID.randomUUID());
        lesson.setLessonIdentityId(UUID.randomUUID());
        lesson.setTitle(type.name() + " lesson");
        lesson.setType(type);
        lesson.setStatus(LessonStatus.PUBLISHED);
        lesson.setPreview(false);
        lesson.setSortOrder(sortOrder);
        lesson.setCreatedAt(Instant.now());
        return lesson;
    }
}
