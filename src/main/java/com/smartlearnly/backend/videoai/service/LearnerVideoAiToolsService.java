package com.smartlearnly.backend.videoai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.videoai.dto.LearnerVideoAiToolsDtos.FlashcardDeckResponse;
import com.smartlearnly.backend.videoai.dto.LearnerVideoAiToolsDtos.FlashcardItem;
import com.smartlearnly.backend.videoai.dto.LearnerVideoAiToolsDtos.GenerateToolRequest;
import com.smartlearnly.backend.videoai.dto.LearnerVideoAiToolsDtos.QuizQuestion;
import com.smartlearnly.backend.videoai.dto.LearnerVideoAiToolsDtos.QuizResponse;
import com.smartlearnly.backend.videoai.dto.LearnerVideoAiToolsDtos.ToolsResponse;
import com.smartlearnly.backend.videoai.dto.VideoAiDtos.ContentResponse;
import com.smartlearnly.backend.videoai.entity.LearnerVideoAiArtifact;
import com.smartlearnly.backend.videoai.repository.LearnerVideoAiArtifactRepository;
import com.smartlearnly.backend.videoai.service.VideoAiLearningService.LearningAvailability;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LearnerVideoAiToolsService {
    private static final String FLASHCARDS = "FLASHCARDS";
    private static final String QUIZ = "QUIZ";
    private static final Duration REGENERATE_COOLDOWN = Duration.ofSeconds(15);

    private final VideoAiLearningService learningService;
    private final LearnerVideoAiArtifactRepository artifactRepository;
    private final CurrentUserService currentUserService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public ToolsResponse getTools(UUID courseId, UUID lessonId, UUID classId) {
        LearningAvailability availability = learningService.getAvailability(courseId, lessonId, classId);
        Optional<ContentResponse> published = availability.content();
        if (published.isEmpty()) {
            return new ToolsResponse(false, availability.preparationStatus(), null, null, null);
        }

        ContentResponse content = published.get();
        UUID studentId = currentUserService.requireAuthenticatedUser().getId();
        return new ToolsResponse(
                true,
                "READY",
                content,
                readArtifact(studentId, content, FLASHCARDS, FlashcardDeckResponse.class).orElse(null),
                readArtifact(studentId, content, QUIZ, QuizResponse.class).orElse(null));
    }

    @Transactional
    public FlashcardDeckResponse generateFlashcards(
            UUID courseId,
            UUID lessonId,
            UUID classId,
            GenerateToolRequest request
    ) {
        ContentResponse content = requireContent(courseId, lessonId, classId);
        UUID studentId = currentUserService.requireAuthenticatedUser().getId();
        int count = requestedCount(request, 8, 20);
        String difficulty = difficulty(request);
        return getOrGenerate(studentId, courseId, classId, content, FLASHCARDS,
                Boolean.TRUE.equals(request == null ? null : request.regenerate()),
                FlashcardDeckResponse.class,
                () -> buildFlashcards(content, count, difficulty));
    }

    @Transactional
    public QuizResponse generateQuiz(
            UUID courseId,
            UUID lessonId,
            UUID classId,
            GenerateToolRequest request
    ) {
        ContentResponse content = requireContent(courseId, lessonId, classId);
        UUID studentId = currentUserService.requireAuthenticatedUser().getId();
        int count = requestedCount(request, 5, 10);
        String difficulty = difficulty(request);
        return getOrGenerate(studentId, courseId, classId, content, QUIZ,
                Boolean.TRUE.equals(request == null ? null : request.regenerate()),
                QuizResponse.class,
                () -> buildQuiz(content, count, difficulty));
    }

    private ContentResponse requireContent(UUID courseId, UUID lessonId, UUID classId) {
        return learningService.getPublished(courseId, lessonId, classId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.CONFLICT,
                        "AI learning tools are not ready for this video yet"));
    }

    private <T> Optional<T> readArtifact(
            UUID studentId,
            ContentResponse content,
            String type,
            Class<T> responseType
    ) {
        return artifactRepository
                .findByStudentIdAndLessonIdAndSourceVersionAndArtifactType(
                        studentId, content.lessonId(), content.sourceVersion(), type)
                .flatMap(value -> deserialize(value.getContentJson(), responseType));
    }

    private <T> T getOrGenerate(
            UUID studentId,
            UUID courseId,
            UUID classId,
            ContentResponse content,
            String type,
            boolean regenerate,
            Class<T> responseType,
            Supplier<T> generator
    ) {
        Optional<LearnerVideoAiArtifact> existing = artifactRepository
                .findByStudentIdAndLessonIdAndSourceVersionAndArtifactType(
                        studentId, content.lessonId(), content.sourceVersion(), type);
        if (existing.isPresent()) {
            Optional<T> cached = deserialize(existing.get().getContentJson(), responseType);
            boolean coolingDown = existing.get().getUpdatedAt() != null
                    && existing.get().getUpdatedAt().isAfter(Instant.now().minus(REGENERATE_COOLDOWN));
            if (!regenerate || coolingDown) return cached.orElseGet(generator);
        }

        T generated = generator.get();
        LearnerVideoAiArtifact artifact = existing.orElseGet(LearnerVideoAiArtifact::new);
        artifact.setStudentId(studentId);
        artifact.setCourseId(courseId);
        artifact.setClassId(classId);
        artifact.setLessonId(content.lessonId());
        artifact.setSourceVersion(content.sourceVersion());
        artifact.setArtifactType(type);
        artifact.setContentJson(serialize(generated));
        artifactRepository.save(artifact);
        return generated;
    }

    private FlashcardDeckResponse buildFlashcards(ContentResponse content, int count, String difficulty) {
        List<String> facts = sourceFacts(content);
        List<FlashcardItem> cards = new ArrayList<>();
        for (int index = 0; index < Math.min(count, facts.size()); index++) {
            String prompt = switch (difficulty) {
                case "hard" -> "Explain this idea from memory: key takeaway " + (index + 1);
                case "easy" -> "What does the video say about key takeaway " + (index + 1) + "?";
                default -> "Recall key takeaway " + (index + 1) + " from the video.";
            };
            cards.add(new FlashcardItem(prompt, facts.get(index)));
        }
        return new FlashcardDeckResponse(List.copyOf(cards), difficulty, Instant.now());
    }

    private QuizResponse buildQuiz(ContentResponse content, int count, String difficulty) {
        List<String> facts = sourceFacts(content);
        List<QuizQuestion> questions = new ArrayList<>();
        int questionCount = Math.min(count, facts.size());
        for (int index = 0; index < questionCount; index++) {
            String correct = facts.get(index);
            List<String> options = new ArrayList<>();
            options.add(correct);
            for (int offset = 1; options.size() < Math.min(4, facts.size()); offset++) {
                String candidate = facts.get((index + offset) % facts.size());
                if (!options.contains(candidate)) options.add(candidate);
            }
            if (options.size() == 1) options.add("This idea was not included in the video.");
            int rotation = index % options.size();
            Collections.rotate(options, rotation);
            int correctIndex = options.indexOf(correct);
            String prompt = difficulty.equals("hard")
                    ? "Which statement best reflects the video's explanation?"
                    : "Which statement is a key takeaway from this video?";
            questions.add(new QuizQuestion(prompt, List.copyOf(options), correctIndex, correct));
        }
        return new QuizResponse(List.copyOf(questions), difficulty, Instant.now());
    }

    private List<String> sourceFacts(ContentResponse content) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (content.keyPoints() != null) content.keyPoints().stream().map(this::normalize)
                .filter(value -> value != null).forEach(values::add);
        if (values.size() < 3 && content.chapters() != null) content.chapters().stream()
                .map(chapter -> normalize(chapter.summary()))
                .filter(value -> value != null).forEach(values::add);
        if (values.isEmpty()) {
            String summary = normalize(content.summary());
            if (summary != null) values.add(summary);
        }
        if (values.isEmpty()) {
            throw new BusinessException(ErrorCode.CONFLICT, "This video does not have enough AI content yet");
        }
        return List.copyOf(values);
    }

    private int requestedCount(GenerateToolRequest request, int fallback, int max) {
        if (request == null || request.desiredCount() == null) return fallback;
        return Math.max(1, Math.min(max, request.desiredCount()));
    }

    private String difficulty(GenerateToolRequest request) {
        String value = normalize(request == null ? null : request.difficulty());
        return value == null ? "medium" : value.toLowerCase(Locale.ROOT);
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            log.error("Could not serialize learner video AI artifact", exception);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Could not save the generated learning tool");
        }
    }

    private <T> Optional<T> deserialize(String value, Class<T> responseType) {
        try {
            return Optional.of(objectMapper.readValue(value, responseType));
        } catch (JsonProcessingException exception) {
            return Optional.empty();
        }
    }

    private String normalize(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
