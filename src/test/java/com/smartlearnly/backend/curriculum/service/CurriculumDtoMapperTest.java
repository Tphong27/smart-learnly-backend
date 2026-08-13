package com.smartlearnly.backend.curriculum.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smartlearnly.backend.curriculum.entity.CurriculumLesson;
import com.smartlearnly.backend.curriculum.entity.CurriculumScope;
import com.smartlearnly.backend.curriculum.entity.CurriculumSection;
import com.smartlearnly.backend.curriculum.entity.CurriculumStatus;
import com.smartlearnly.backend.curriculum.entity.CurriculumVersion;
import com.smartlearnly.backend.learning.content.dto.LearningContentResponse;
import com.smartlearnly.backend.learning.content.dto.LearningLessonResponse;
import com.smartlearnly.backend.learning.lesson.entity.LessonStatus;
import com.smartlearnly.backend.learning.lesson.entity.LessonType;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CurriculumDtoMapperTest {

    private final CurriculumDtoMapper mapper = new CurriculumDtoMapper(
            org.mockito.Mockito.mock(ClassCurriculumCompositionService.class));

    @Test
    void toLearningLessonResponseIncludesYoutubeVideoUrl() {
        CurriculumLesson lesson = videoLesson();

        LearningLessonResponse response = mapper.toLearningLessonResponse(
                lesson,
                false);

        assertEquals(
                "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                response.videoUrl());
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
                null);

        List<LearningLessonResponse> lessons = response.sections().get(0).lessons();
        assertEquals(
                List.of("VIDEO", "RICH_TEXT", "QUIZ", "FLASHCARD", "ESSAY"),
                lessons.stream().map(LearningLessonResponse::lessonType).toList());
        assertEquals(5, response.stats().totalLessons());
        assertTrue(lessons.get(1).completed());
        assertEquals(
                "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                lessons.get(0).videoUrl());
    }

    @Test
    void toLearningContentResponseOmitsDraftFlashcardLessonsForTrainee() {
        UUID courseId = UUID.randomUUID();
        CurriculumVersion version = new CurriculumVersion();
        version.setId(UUID.randomUUID());
        version.setCourseId(courseId);
        version.setScope(CurriculumScope.MASTER);
        version.setStatus(CurriculumStatus.PUBLISHED);
        version.setVersionNumber(1);

        CurriculumSection section = new CurriculumSection();
        section.setId(UUID.randomUUID());
        section.setTitle("Flashcards");
        section.setSortOrder(0);
        section.setCreatedAt(Instant.now());
        version.addSection(section);

        CurriculumLesson publishedFlashcard = publishedLesson(LessonType.FLASHCARD, 0);
        publishedFlashcard.setTitle("Published flashcards");
        CurriculumLesson draftFlashcard = publishedLesson(LessonType.FLASHCARD, 1);
        draftFlashcard.setTitle("Draft visibility integration test");
        draftFlashcard.setStatus(LessonStatus.DRAFT);
        section.addLesson(publishedFlashcard);
        section.addLesson(draftFlashcard);

        LearningContentResponse response = mapper.toLearningContentResponse(
                version,
                "Course",
                null,
                Set.of(),
                null);

        List<LearningLessonResponse> lessons = response.sections().get(0).lessons();
        assertEquals(List.of("Published flashcards"), lessons.stream().map(LearningLessonResponse::title).toList());
        assertEquals(1, response.stats().totalLessons());
    }

    @Test
    void toLearningContentResponseOmitsDraftClassFlashcardEffectiveLessonsForTrainee() {
        ClassCurriculumCompositionService compositionService =
                org.mockito.Mockito.mock(ClassCurriculumCompositionService.class);
        CurriculumDtoMapper classMapper = new CurriculumDtoMapper(compositionService);
        UUID courseId = UUID.randomUUID();
        CurriculumVersion version = new CurriculumVersion();
        version.setId(UUID.randomUUID());
        version.setCourseId(courseId);
        version.setClassId(UUID.randomUUID());
        version.setScope(CurriculumScope.CLASS);
        version.setStatus(CurriculumStatus.PUBLISHED);
        version.setVersionNumber(1);

        CurriculumSection section = new CurriculumSection();
        section.setId(UUID.randomUUID());
        section.setTitle("Class flashcards");
        section.setSortOrder(0);
        section.setCreatedAt(Instant.now());
        version.addSection(section);

        CurriculumLesson publishedFlashcard = publishedLesson(LessonType.FLASHCARD, 0);
        publishedFlashcard.setTitle("Published class flashcards");
        CurriculumLesson draftFlashcard = publishedLesson(LessonType.FLASHCARD, 1);
        draftFlashcard.setTitle("Hidden draft class flashcards");
        draftFlashcard.setStatus(LessonStatus.DRAFT);
        section.addLesson(publishedFlashcard);
        section.addLesson(draftFlashcard);

        org.mockito.Mockito.when(compositionService.isCompositionVersion(version)).thenReturn(true);
        org.mockito.Mockito.when(compositionService.effectiveLessons(section))
                .thenReturn(List.of(publishedFlashcard, draftFlashcard));

        LearningContentResponse response = classMapper.toLearningContentResponse(
                version,
                "Class course",
                null,
                Set.of(),
                null);

        List<LearningLessonResponse> lessons = response.sections().get(0).lessons();
        assertEquals(
                List.of("Published class flashcards"),
                lessons.stream().map(LearningLessonResponse::title).toList());
        assertEquals(1, response.stats().totalLessons());
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
        if (type == LessonType.VIDEO) {
            lesson.setVideoUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ");
        }
        lesson.setStatus(LessonStatus.PUBLISHED);
        lesson.setPreview(false);
        lesson.setSortOrder(sortOrder);
        lesson.setCreatedAt(Instant.now());
        return lesson;
    }
}
