package com.smartlearnly.backend.test.definition.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlearnly.backend.curriculum.entity.CurriculumLesson;
import com.smartlearnly.backend.curriculum.entity.CurriculumSection;
import com.smartlearnly.backend.curriculum.entity.CurriculumVersion;
import com.smartlearnly.backend.curriculum.repository.CurriculumLessonRepository;
import com.smartlearnly.backend.question.entity.Question;
import com.smartlearnly.backend.question.entity.QuestionAnswer;
import com.smartlearnly.backend.question.entity.QuestionStatus;
import com.smartlearnly.backend.question.entity.QuestionType;
import com.smartlearnly.backend.question.repository.QuestionAnswerRepository;
import com.smartlearnly.backend.question.repository.QuestionRepository;
import com.smartlearnly.backend.test.definition.dto.TestQuestionModel;
import com.smartlearnly.backend.test.definition.mapping.TestQuestionMapper;
import com.smartlearnly.backend.test.entity.TestQuestion;
import com.smartlearnly.backend.test.entity.TestQuestion.TestQuestionId;
import com.smartlearnly.backend.test.repository.TestQuestionRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TestQuestionService {

    private final TestQuestionRepository repository;
    private final TestQuestionMapper mapper;
    private final CurriculumLessonRepository curriculumLessonRepository;
    private final QuestionRepository questionRepository;
    private final QuestionAnswerRepository answerRepository;
    private final ObjectMapper objectMapper;

    public TestQuestionModel.Response addQuestionToTest(TestQuestionModel.AddRequest request) {
        TestQuestion entity = new TestQuestion();
        entity.setId(new TestQuestionId(request.getTestId(), request.getQuestionId()));
        entity.setOrderIndex(request.getOrderIndex());
        entity.setMarks(request.getMarks());
        return mapper.toResponse(repository.save(entity));
    }

    public List<TestQuestionModel.Response> getQuestionsByTest(UUID testId) {
        return repository.findByIdTestId(testId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional
    public List<TestQuestionModel.LearnerResponse> getLearnerQuestionsByTest(UUID testId) {
        ensureQuestionsFromQuizLessonContent(testId);
        return repository.findByIdTestId(testId).stream()
                .map(mapper::toLearnerResponse)
                .toList();
    }

    public TestQuestionModel.Response updateTestQuestion(
            UUID testId,
            UUID questionId,
            TestQuestionModel.UpdateRequest request) {
        TestQuestionId id = new TestQuestionId(testId, questionId);
        TestQuestion entity = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Test question not found"));
        entity.setOrderIndex(request.getOrderIndex());
        entity.setMarks(request.getMarks());
        return mapper.toResponse(repository.save(entity));
    }

    public void removeQuestionFromTest(UUID testId, UUID questionId) {
        TestQuestionId id = new TestQuestionId(testId, questionId);
        if (!repository.existsById(id)) {
            throw new EntityNotFoundException("Test question not found");
        }
        repository.deleteById(id);
    }

    private void ensureQuestionsFromQuizLessonContent(UUID testId) {
        if (testId == null || !repository.findByIdTestId(testId).isEmpty()) {
            return;
        }
        curriculumLessonRepository.findFirstByTestId(testId)
                .ifPresent(lesson -> importQuizContentQuestions(testId, lesson));
    }

    private void importQuizContentQuestions(UUID testId, CurriculumLesson lesson) {
        JsonNode questions = readQuizQuestions(lesson.getContent());
        if (questions == null || !questions.isArray() || questions.isEmpty()) {
            return;
        }

        CurriculumSection section = lesson.getSection();
        CurriculumVersion version = section == null ? null : section.getCurriculumVersion();
        UUID courseId = version == null ? null : version.getCourseId();
        UUID moduleId = section == null ? null : section.getSourceModuleId();
        if (courseId == null || moduleId == null) {
            return;
        }

        int orderIndex = 0;
        for (JsonNode rawQuestion : questions) {
            PersistableQuizQuestion parsed = parsePersistableQuestion(rawQuestion);
            if (parsed == null) {
                continue;
            }

            Question savedQuestion = findExistingQuestion(courseId, parsed.title());
            if (savedQuestion == null) {
                Question question = new Question();
                question.setCourseId(courseId);
                question.setModuleId(moduleId);
                question.setQuestionText(parsed.title());
                question.setQuestionType(parsed.type());
                question.setExplanation(parsed.explanation());
                question.setIsAiGenerated(false);
                question.setStatus(QuestionStatus.APPROVED);
                savedQuestion = questionRepository.save(question);

                for (int index = 0; index < parsed.answers().size(); index++) {
                    PersistableQuizAnswer parsedAnswer = parsed.answers().get(index);
                    QuestionAnswer answer = new QuestionAnswer();
                    answer.setQuestionId(savedQuestion.getId());
                    answer.setAnswerText(parsedAnswer.text());
                    answer.setIsCorrect(parsedAnswer.correct());
                    answer.setOrderIndex(index + 1);
                    answerRepository.save(answer);
                }
            }

            TestQuestion entity = new TestQuestion();
            entity.setId(new TestQuestionId(testId, savedQuestion.getId()));
            entity.setOrderIndex(orderIndex++);
            entity.setMarks(BigDecimal.ONE);
            repository.save(entity);
        }
    }

    private Question findExistingQuestion(UUID courseId, String questionText) {
        return questionRepository.findExactDuplicateCandidatesInCourse(courseId, questionText)
                .stream()
                .filter(question -> question.getStatus() != QuestionStatus.ARCHIVED)
                .findFirst()
                .orElse(null);
    }

    private JsonNode readQuizQuestions(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(content);
            return root == null ? null : root.get("questions");
        } catch (Exception ignored) {
            return null;
        }
    }

    private PersistableQuizQuestion parsePersistableQuestion(JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }
        String title = textValue(node, "title");
        if (title == null || title.isBlank()) {
            title = textValue(node, "question");
        }
        if (title == null || title.isBlank()) {
            return null;
        }

        QuestionType type = mapQuestionType(textValue(node, "type"));
        JsonNode options = node.get("options");
        if (type == null || options == null || !options.isArray() || options.size() < 2) {
            return null;
        }

        List<Integer> correctIndexes = readCorrectIndexes(node);
        if (correctIndexes.isEmpty() && node.has("correctIndex")) {
            correctIndexes = List.of(node.path("correctIndex").asInt(-1) + 1);
        }
        if (correctIndexes.isEmpty()) {
            return null;
        }

        List<PersistableQuizAnswer> answers = new ArrayList<>();
        for (int index = 0; index < options.size(); index++) {
            String answerText = optionText(options.get(index));
            if (answerText == null || answerText.isBlank()) {
                return null;
            }
            answers.add(new PersistableQuizAnswer(answerText, correctIndexes.contains(index + 1)));
        }

        return new PersistableQuizQuestion(
                title.trim(),
                type,
                blankToNull(textValue(node, "explain_question")),
                answers);
    }

    private QuestionType mapQuestionType(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            return QuestionType.MULTIPLE_CHOICE;
        }
        String normalized = rawType.trim().replace('-', '_').toLowerCase(Locale.ROOT);
        if ("single_choice".equals(normalized) || "multiple_choice".equals(normalized)) {
            return QuestionType.MULTIPLE_CHOICE;
        }
        if ("true_false".equals(normalized)) {
            return QuestionType.TRUE_FALSE;
        }
        return null;
    }

    private List<Integer> readCorrectIndexes(JsonNode node) {
        JsonNode correctAnswers = node.get("correct_answers");
        if (correctAnswers == null || !correctAnswers.isArray()) {
            return List.of();
        }
        List<Integer> indexes = new ArrayList<>();
        correctAnswers.forEach(value -> {
            if (value.canConvertToInt()) {
                indexes.add(value.asInt());
            }
        });
        return indexes;
    }

    private String optionText(JsonNode option) {
        if (option == null || option.isNull()) {
            return null;
        }
        if (option.isTextual() || option.isNumber() || option.isBoolean()) {
            return option.asText();
        }
        return textValue(option, "text");
    }

    private String textValue(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record PersistableQuizQuestion(
            String title,
            QuestionType type,
            String explanation,
            List<PersistableQuizAnswer> answers
    ) {
    }

    private record PersistableQuizAnswer(String text, boolean correct) {
    }
}
