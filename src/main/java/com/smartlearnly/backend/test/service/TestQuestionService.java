package com.smartlearnly.backend.test.service;

import com.smartlearnly.backend.question.dto.QuestionModel;
import com.smartlearnly.backend.question.entity.Question;
import com.smartlearnly.backend.question.entity.QuestionMediaAttachment;
import com.smartlearnly.backend.question.entity.QuestionMediaType;
import com.smartlearnly.backend.question.repository.QuestionAnswerRepository;
import com.smartlearnly.backend.question.repository.QuestionMediaAttachmentRepository;
import com.smartlearnly.backend.question.repository.QuestionRepository;
import com.smartlearnly.backend.test.dto.TestQuestionModel;
import com.smartlearnly.backend.test.entity.TestQuestion;
import com.smartlearnly.backend.test.entity.TestQuestion.TestQuestionId;
import com.smartlearnly.backend.test.repository.TestQuestionRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TestQuestionService {

    private final TestQuestionRepository repository;
    private final QuestionRepository questionRepository;
    private final QuestionAnswerRepository answerRepository;
    private final QuestionMediaAttachmentRepository mediaAttachmentRepository;

    public TestQuestionModel.Response addQuestionToTest(
            TestQuestionModel.AddRequest request) {

        TestQuestion entity = new TestQuestion();

        entity.setId(new TestQuestionId(
                request.getTestId(),
                request.getQuestionId()));

        entity.setOrderIndex(
                request.getOrderIndex());

        entity.setMarks(request.getMarks());

        TestQuestion saved = repository.save(entity);

        return mapToResponse(saved);
    }

    public List<TestQuestionModel.Response>
    getQuestionsByTest(UUID testId) {

        List<TestQuestion> entities =
                repository.findByIdTestId(testId);

        List<TestQuestionModel.Response> responses =
                new ArrayList<>();

        for (TestQuestion entity : entities) {
            responses.add(mapToResponse(entity));
        }

        return responses;
    }

    public List<TestQuestionModel.LearnerResponse>
    getLearnerQuestionsByTest(UUID testId) {

        List<TestQuestion> entities =
                repository.findByIdTestId(testId);

        List<TestQuestionModel.LearnerResponse> responses =
                new ArrayList<>();

        for (TestQuestion entity : entities) {
            responses.add(mapToLearnerResponse(entity));
        }

        return responses;
    }

    public TestQuestionModel.Response updateTestQuestion(
            UUID testId,
            UUID questionId,
            TestQuestionModel.UpdateRequest request) {

        TestQuestionId id =
                new TestQuestionId(testId, questionId);

        TestQuestion entity = repository.findById(id)
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "Test question not found"));

        entity.setOrderIndex(
                request.getOrderIndex());

        entity.setMarks(request.getMarks());

        TestQuestion updated = repository.save(entity);

        return mapToResponse(updated);
    }

    public void removeQuestionFromTest(
            UUID testId,
            UUID questionId) {

        TestQuestionId id =
                new TestQuestionId(testId, questionId);

        if (!repository.existsById(id)) {
            throw new EntityNotFoundException(
                    "Test question not found");
        }

        repository.deleteById(id);
    }

    private TestQuestionModel.Response mapToResponse(
            TestQuestion entity) {

        TestQuestionModel.Response response =
                new TestQuestionModel.Response();

        populateBaseResponse(response, entity);

        questionRepository.findById(entity.getId().getQuestionId())
                .ifPresent(question -> appendQuestionDetails(response, question));

        return response;
    }

    private TestQuestionModel.LearnerResponse mapToLearnerResponse(
            TestQuestion entity) {

        TestQuestionModel.LearnerResponse response =
                new TestQuestionModel.LearnerResponse();

        response.setTestId(entity.getId().getTestId());
        response.setQuestionId(entity.getId().getQuestionId());
        response.setOrderIndex(entity.getOrderIndex());
        response.setMarks(entity.getMarks());

        questionRepository.findById(entity.getId().getQuestionId())
                .ifPresent(question -> appendLearnerQuestionDetails(response, question));

        return response;
    }

    private void populateBaseResponse(
            TestQuestionModel.Response response,
            TestQuestion entity) {

        response.setTestId(
                entity.getId().getTestId());

        response.setQuestionId(
                entity.getId().getQuestionId());

        response.setOrderIndex(
                entity.getOrderIndex());

        response.setMarks(
                entity.getMarks());
    }

    private void appendQuestionDetails(
            TestQuestionModel.Response response,
            Question question) {

        response.setQuestionText(question.getQuestionText());
        response.setImageUrl(primaryMediaUrl(question, QuestionMediaType.IMAGE));
        response.setAudioUrl(primaryMediaUrl(question, QuestionMediaType.AUDIO));
        response.setQuestionType(question.getQuestionType() == null
                ? null
                : question.getQuestionType().name().toLowerCase());
        response.setAnswers(answerRepository
                .findByQuestionIdOrderByOrderIndexAsc(question.getId())
                .stream()
                .map(answer -> new QuestionModel.AnswerResponse(
                        answer.getId(),
                        answer.getId(),
                        answer.getAnswerText(),
                        Boolean.TRUE.equals(answer.getIsCorrect()),
                        Boolean.TRUE.equals(answer.getIsCorrect()),
                        answer.getOrderIndex() == null ? 0 : answer.getOrderIndex(),
                        answer.getOrderIndex() == null ? 0 : answer.getOrderIndex()))
                .toList());
    }

    private String primaryMediaUrl(Question question, QuestionMediaType mediaType) {
        return mediaAttachmentRepository.findFirstByQuestionIdAndMediaTypeOrderByDisplayOrderAsc(question.getId(), mediaType)
                .map(QuestionMediaAttachment::getMediaUrl)
                .orElse(null);
    }
    private void appendLearnerQuestionDetails(
            TestQuestionModel.LearnerResponse response,
            Question question) {

        response.setQuestionText(question.getQuestionText());
        response.setImageUrl(primaryMediaUrl(question, QuestionMediaType.IMAGE));
        response.setAudioUrl(primaryMediaUrl(question, QuestionMediaType.AUDIO));
        response.setQuestionType(question.getQuestionType() == null
                ? null
                : question.getQuestionType().name().toLowerCase());
        response.setAnswers(answerRepository
                .findByQuestionIdOrderByOrderIndexAsc(question.getId())
                .stream()
                .map(answer -> {
                    TestQuestionModel.LearnerAnswerResponse learnerAnswer =
                            new TestQuestionModel.LearnerAnswerResponse();
                    Integer order = answer.getOrderIndex() == null ? 0 : answer.getOrderIndex();
                    learnerAnswer.setAnswerId(answer.getId());
                    learnerAnswer.setId(answer.getId());
                    learnerAnswer.setAnswerText(answer.getAnswerText());
                    learnerAnswer.setDisplayOrder(order);
                    learnerAnswer.setOrderIndex(order);
                    return learnerAnswer;
                })
                .toList());
    }
}

