package com.smartlearnly.backend.videoai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.user.entity.UserAccount;
import com.smartlearnly.backend.videoai.dto.LearnerVideoAiToolsDtos.GenerateToolRequest;
import com.smartlearnly.backend.videoai.dto.LearnerVideoAiToolsDtos.QuizResponse;
import com.smartlearnly.backend.videoai.dto.VideoAiDtos.ContentResponse;
import com.smartlearnly.backend.videoai.entity.LearnerVideoAiArtifact;
import com.smartlearnly.backend.videoai.repository.LearnerVideoAiArtifactRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LearnerVideoAiToolsServiceTest {
    @Mock
    private VideoAiLearningService learningService;
    @Mock
    private LearnerVideoAiArtifactRepository artifactRepository;
    @Mock
    private CurrentUserService currentUserService;

    private LearnerVideoAiToolsService service;

    @BeforeEach
    void setUp() {
        service = new LearnerVideoAiToolsService(
                learningService,
                artifactRepository,
                currentUserService,
                new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void generateFlashcardsStoresPrivateArtifactForCurrentLearner() {
        UUID studentId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID lessonId = UUID.randomUUID();
        UUID sourceVersion = UUID.randomUUID();
        UserAccount student = new UserAccount();
        student.setId(studentId);
        ContentResponse content = new ContentResponse(
                UUID.randomUUID(),
                lessonId,
                sourceVersion,
                "en",
                "Transcript",
                "Lesson title",
                "Lesson summary",
                List.of("First takeaway", "Second takeaway"),
                "published",
                1L,
                List.of(),
                List.of(),
                Instant.now(),
                Instant.now());

        when(learningService.getPublished(courseId, lessonId, null)).thenReturn(Optional.of(content));
        when(currentUserService.requireAuthenticatedUser()).thenReturn(student);
        when(artifactRepository.findByStudentIdAndLessonIdAndSourceVersionAndArtifactType(
                studentId, lessonId, sourceVersion, "FLASHCARDS")).thenReturn(Optional.empty());

        var result = service.generateFlashcards(
                courseId,
                lessonId,
                null,
                new GenerateToolRequest(2, "medium", false));

        assertThat(result.cards()).hasSize(2);
        assertThat(result.cards()).extracting(card -> card.back())
                .containsExactly("First takeaway", "Second takeaway");
        ArgumentCaptor<LearnerVideoAiArtifact> artifactCaptor =
                ArgumentCaptor.forClass(LearnerVideoAiArtifact.class);
        verify(artifactRepository).save(artifactCaptor.capture());
        assertThat(artifactCaptor.getValue().getStudentId()).isEqualTo(studentId);
        assertThat(artifactCaptor.getValue().getLessonId()).isEqualTo(lessonId);
        assertThat(artifactCaptor.getValue().getArtifactType()).isEqualTo("FLASHCARDS");
    }

    @Test
    void generateQuizStoresSerializablePrivateArtifactForCurrentLearner() {
        UUID studentId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID lessonId = UUID.randomUUID();
        UUID sourceVersion = UUID.randomUUID();
        UserAccount student = new UserAccount();
        student.setId(studentId);
        ContentResponse content = new ContentResponse(
                UUID.randomUUID(), lessonId, sourceVersion, "en", "Transcript", "Lesson title",
                "Lesson summary", List.of("First takeaway", "Second takeaway", "Third takeaway"),
                "published", 1L, List.of(), List.of(), Instant.now(), Instant.now());

        when(learningService.getPublished(courseId, lessonId, null)).thenReturn(Optional.of(content));
        when(currentUserService.requireAuthenticatedUser()).thenReturn(student);
        when(artifactRepository.findByStudentIdAndLessonIdAndSourceVersionAndArtifactType(
                studentId, lessonId, sourceVersion, "QUIZ")).thenReturn(Optional.empty());

        QuizResponse result = service.generateQuiz(
                courseId, lessonId, null, new GenerateToolRequest(3, "medium", false));

        assertThat(result.questions()).hasSize(3);
        ArgumentCaptor<LearnerVideoAiArtifact> artifactCaptor =
                ArgumentCaptor.forClass(LearnerVideoAiArtifact.class);
        verify(artifactRepository).save(artifactCaptor.capture());
        assertThat(artifactCaptor.getValue().getArtifactType()).isEqualTo("QUIZ");
        assertThat(artifactCaptor.getValue().getContentJson()).contains("generatedAt");
    }
}
