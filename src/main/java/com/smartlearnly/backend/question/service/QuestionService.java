
package com.smartlearnly.backend.question.service;

import com.smartlearnly.backend.question.dto.QuestionModel;
import com.smartlearnly.backend.question.entity.Question;
import com.smartlearnly.backend.question.entity.QuestionStatus;
import com.smartlearnly.backend.question.repository.QuestionRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class QuestionService {

    private final QuestionRepository questionRepository;

    public QuestionModel.Response create(
            QuestionModel.CreateRequest request) {

        Question question = new Question();

        question.setQuestionBankId(
                request.getQuestionBankId());
        question.setCourseId(request.getCourseId());
        question.setCloId(request.getCloId());
        question.setQuestionText(
                request.getQuestionText());
        question.setQuestionType(
                request.getQuestionType());
        question.setBloomLevel(
                request.getBloomLevel());
        question.setDifficulty(
                request.getDifficulty());
        question.setExplanation(
                request.getExplanation());
        question.setIsAiGenerated(
                request.getIsAiGenerated());
        question.setStatus(
                request.getStatus() != null
                        ? request.getStatus()
                        : QuestionStatus.DRAFT);

        Question saved = questionRepository.save(question);

        return mapToResponse(saved);
    }

    public List<QuestionModel.Response> getAll() {

        List<Question> questions =
                questionRepository.findAll();

        List<QuestionModel.Response> responses =
                new ArrayList<>();

        for (Question question : questions) {
            responses.add(mapToResponse(question));
        }

        return responses;
    }

    public QuestionModel.Response getById(UUID id) {

        Question question = questionRepository.findById(id)
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "Question not found"));

        return mapToResponse(question);
    }

    public QuestionModel.Response update(
            UUID id,
            QuestionModel.UpdateRequest request) {

        Question question = questionRepository.findById(id)
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "Question not found"));

        question.setCloId(request.getCloId());
        question.setQuestionText(
                request.getQuestionText());
        question.setQuestionType(
                request.getQuestionType());
        question.setBloomLevel(
                request.getBloomLevel());
        question.setDifficulty(
                request.getDifficulty());
        question.setExplanation(
                request.getExplanation());
        question.setStatus(request.getStatus());

        question.setReviewedBy(
                request.getReviewedBy());

        if (request.getReviewedBy() != null) {
            question.setReviewedAt(Instant.now());
        }

        Question updated = questionRepository.save(question);

        return mapToResponse(updated);
    }

    public void delete(UUID id) {

        if (!questionRepository.existsById(id)) {
            throw new EntityNotFoundException(
                    "Question not found");
        }

        questionRepository.deleteById(id);
    }

    private QuestionModel.Response mapToResponse(
            Question question) {

        QuestionModel.Response response =
                new QuestionModel.Response();

        response.setId(question.getId());
        response.setQuestionBankId(
                question.getQuestionBankId());
        response.setCourseId(question.getCourseId());
        response.setCloId(question.getCloId());
        response.setQuestionText(
                question.getQuestionText());
        response.setQuestionType(
                question.getQuestionType());
        response.setBloomLevel(
                question.getBloomLevel());
        response.setDifficulty(
                question.getDifficulty());
        response.setExplanation(
                question.getExplanation());
        response.setIsAiGenerated(
                question.getIsAiGenerated());
        response.setStatus(question.getStatus());
        response.setCreatedBy(
                question.getCreatedBy());
        response.setReviewedBy(
                question.getReviewedBy());
        response.setReviewedAt(
                question.getReviewedAt());
        response.setCreatedAt(
                question.getCreatedAt());
        response.setUpdatedAt(
                question.getUpdatedAt());

        return response;
    }
}

