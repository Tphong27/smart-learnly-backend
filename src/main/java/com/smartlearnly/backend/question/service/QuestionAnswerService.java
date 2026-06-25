
package com.smartlearnly.backend.question.service;

import com.smartlearnly.backend.question.dto.QuestionAnswerModel;
import com.smartlearnly.backend.question.entity.QuestionAnswer;
import com.smartlearnly.backend.question.repository.QuestionAnswerRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class QuestionAnswerService {

    private final QuestionAnswerRepository repository;

    public QuestionAnswerModel.Response create(
            QuestionAnswerModel.CreateRequest request) {

        QuestionAnswer answer = new QuestionAnswer();

        answer.setQuestionId(request.getQuestionId());
        answer.setAnswerText(request.getAnswerText());
        answer.setIsCorrect(request.getIsCorrect());
        answer.setOrderIndex(request.getOrderIndex());

        QuestionAnswer saved = repository.save(answer);

        return mapToResponse(saved);
    }

    public List<QuestionAnswerModel.Response>
    getAnswersByQuestion(UUID questionId) {

        List<QuestionAnswer> answers =
                repository.findByQuestionId(questionId);

        List<QuestionAnswerModel.Response> responses =
                new ArrayList<>();

        for (QuestionAnswer answer : answers) {
            responses.add(mapToResponse(answer));
        }

        return responses;
    }

    public QuestionAnswerModel.Response update(
            UUID id,
            QuestionAnswerModel.UpdateRequest request) {

        QuestionAnswer answer = repository.findById(id)
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "Answer not found"));

        answer.setAnswerText(request.getAnswerText());
        answer.setIsCorrect(request.getIsCorrect());
        answer.setOrderIndex(request.getOrderIndex());

        QuestionAnswer updated = repository.save(answer);

        return mapToResponse(updated);
    }

    public void delete(UUID id) {

        if (!repository.existsById(id)) {
            throw new EntityNotFoundException(
                    "Answer not found");
        }

        repository.deleteById(id);
    }

    private QuestionAnswerModel.Response mapToResponse(
            QuestionAnswer answer) {

        QuestionAnswerModel.Response response =
                new QuestionAnswerModel.Response();

        response.setId(answer.getId());
        response.setQuestionId(answer.getQuestionId());
        response.setAnswerText(answer.getAnswerText());
        response.setIsCorrect(answer.getIsCorrect());
        response.setOrderIndex(answer.getOrderIndex());

        return response;
    }
}

