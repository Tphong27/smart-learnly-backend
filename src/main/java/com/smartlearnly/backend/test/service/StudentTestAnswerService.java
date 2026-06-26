
package com.smartlearnly.backend.test.service;

import com.smartlearnly.backend.test.dto.StudentTestAnswerModel;
import com.smartlearnly.backend.test.entity.StudentTestAnswer;
import com.smartlearnly.backend.test.entity.TestAttempt;
import com.smartlearnly.backend.test.repository.StudentTestAnswerRepository;
import com.smartlearnly.backend.test.repository.TestAttemptRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StudentTestAnswerService {

    private final StudentTestAnswerRepository repository;
    private final TestAttemptRepository attemptRepository;

    public StudentTestAnswerModel.Response saveStudentAnswer(
            StudentTestAnswerModel.SaveRequest request) {

        TestAttempt attempt = attemptRepository.findById(request.getAttemptId())
                .orElseThrow(() ->
                        new EntityNotFoundException("Attempt not found"));

        if (attempt.getEndTime() != null && Instant.now().isAfter(attempt.getEndTime())) {
            throw new IllegalStateException("Attempt has expired");
        }

        StudentTestAnswer entity = repository
                .findByAttemptIdAndQuestionId(request.getAttemptId(), request.getQuestionId())
                .orElseGet(StudentTestAnswer::new);

        entity.setAttemptId(request.getAttemptId());
        entity.setQuestionId(request.getQuestionId());
        entity.setSelectedAnswerId(
                request.getSelectedAnswerId());
        entity.setEssayAnswer(
                request.getEssayAnswer());

        StudentTestAnswer saved =
                repository.save(entity);

        return mapToResponse(saved);
    }

    public StudentTestAnswerModel.Response gradeStudentAnswer(
            UUID id,
            StudentTestAnswerModel.GradeRequest request) {

        StudentTestAnswer entity =
                repository.findById(id)
                        .orElseThrow(() ->
                                new EntityNotFoundException(
                                        "Student answer not found"));

        entity.setIsCorrect(
                request.getIsCorrect());

        entity.setScoreAwarded(
                request.getScoreAwarded());

        entity.setIssueReported(
                request.getIssueReported());

        StudentTestAnswer updated =
                repository.save(entity);

        return mapToResponse(updated);
    }

    public List<StudentTestAnswerModel.Response>
    getAnswersByAttempt(UUID attemptId) {

        List<StudentTestAnswer> entities =
                repository.findByAttemptId(attemptId);

        List<StudentTestAnswerModel.Response> responses =
                new ArrayList<>();

        for (StudentTestAnswer entity : entities) {
            responses.add(mapToResponse(entity));
        }

        return responses;
    }

    private StudentTestAnswerModel.Response mapToResponse(
            StudentTestAnswer entity) {

        StudentTestAnswerModel.Response response =
                new StudentTestAnswerModel.Response();

        response.setId(entity.getId());
        response.setAttemptId(
                entity.getAttemptId());
        response.setQuestionId(
                entity.getQuestionId());
        response.setSelectedAnswerId(
                entity.getSelectedAnswerId());
        response.setEssayAnswer(
                entity.getEssayAnswer());
        response.setIsCorrect(
                entity.getIsCorrect());
        response.setScoreAwarded(
                entity.getScoreAwarded());
        response.setIssueReported(
                entity.getIssueReported());

        return response;
    }
}

