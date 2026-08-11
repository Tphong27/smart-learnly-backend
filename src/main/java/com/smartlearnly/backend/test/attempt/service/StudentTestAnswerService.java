
package com.smartlearnly.backend.test.attempt.service;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.question.repository.QuestionAnswerRepository;
import com.smartlearnly.backend.test.attempt.dto.StudentTestAnswerModel;
import com.smartlearnly.backend.test.definition.service.TestService;
import com.smartlearnly.backend.test.entity.AttemptStatus;
import com.smartlearnly.backend.test.entity.StudentTestAnswer;
import com.smartlearnly.backend.test.entity.TestAttempt;
import com.smartlearnly.backend.test.entity.TestQuestion.TestQuestionId;
import com.smartlearnly.backend.test.repository.StudentTestAnswerRepository;
import com.smartlearnly.backend.test.repository.TestAttemptRepository;
import com.smartlearnly.backend.test.repository.TestQuestionRepository;
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
    private final QuestionAnswerRepository questionAnswerRepository;
    private final TestQuestionRepository testQuestionRepository;
    private final TestService testService;

    /** Lưu hoặc cập nhật câu trả lời đang làm của học viên khi attempt còn hiệu lực. */
    public StudentTestAnswerModel.Response saveStudentAnswer(
            StudentTestAnswerModel.SaveRequest request) {

        TestAttempt attempt = attemptRepository.findById(request.getAttemptId())
                .orElseThrow(() ->
                        new EntityNotFoundException("Attempt not found"));

        testService.requireAttemptAccess(attempt.getTestId(), attempt.getStudentId());

        if (attempt.getEndTime() != null && Instant.now().isAfter(attempt.getEndTime())) {
            throw new IllegalStateException("Attempt has expired");
        }
        if (attempt.getStatus() != null
                && attempt.getStatus() != AttemptStatus.DOING
                && attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Attempt is no longer accepting answers");
        }
        if (!testQuestionRepository.existsById(
                new TestQuestionId(attempt.getTestId(), request.getQuestionId()))) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Question does not belong to this test");
        }
        if (request.getSelectedAnswerId() != null) {
            questionAnswerRepository.findById(request.getSelectedAnswerId())
                    .filter(answer -> request.getQuestionId().equals(answer.getQuestionId()))
                    .orElseThrow(() -> new BusinessException(
                            ErrorCode.INVALID_REQUEST,
                            "Selected answer does not belong to this question"));
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

    /** Lưu kết quả chấm thủ công cho một câu trả lời của học viên. */
    public StudentTestAnswerModel.Response gradeStudentAnswer(
            UUID id,
            StudentTestAnswerModel.GradeRequest request) {

        StudentTestAnswer entity =
                repository.findById(id)
                        .orElseThrow(() ->
                                new EntityNotFoundException(
                                        "Student answer not found"));
        TestAttempt attempt = attemptRepository.findById(entity.getAttemptId())
                .orElseThrow(() -> new EntityNotFoundException("Attempt not found"));
        testService.requireAttemptAccess(attempt.getTestId(), attempt.getStudentId());

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

    /** Trả các câu trả lời đã lưu trong một attempt để người học hoặc giảng viên xem lại. */
    public List<StudentTestAnswerModel.Response>
    getAnswersByAttempt(UUID attemptId) {

        TestAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new EntityNotFoundException("Attempt not found"));
        testService.requireAttemptAccess(attempt.getTestId(), attempt.getStudentId());

        List<StudentTestAnswer> entities =
                repository.findByAttemptId(attemptId);

        List<StudentTestAnswerModel.Response> responses =
                new ArrayList<>();

        for (StudentTestAnswer entity : entities) {
            responses.add(mapToResponse(entity));
        }

        return responses;
    }

    /** Chuyển entity câu trả lời thành dữ liệu API kèm đáp án đúng khi đã chấm. */
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
        if (entity.getIsCorrect() != null) {
            response.setCorrectAnswerId(questionAnswerRepository
                    .findByQuestionId(entity.getQuestionId())
                    .stream()
                    .filter(answer -> Boolean.TRUE.equals(answer.getIsCorrect()))
                    .map(answer -> answer.getId())
                    .findFirst()
                    .orElse(null));
        }
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

