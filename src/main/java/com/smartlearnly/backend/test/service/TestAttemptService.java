package com.smartlearnly.backend.test.service;

import com.smartlearnly.backend.flashtest.dto.MonitorEvent;
import com.smartlearnly.backend.question.entity.QuestionAnswer;
import com.smartlearnly.backend.question.repository.QuestionAnswerRepository;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.test.dto.TestAttemptModel;
import com.smartlearnly.backend.test.entity.AttemptStatus;
import com.smartlearnly.backend.test.entity.StudentTestAnswer;
import com.smartlearnly.backend.test.entity.Test;
import com.smartlearnly.backend.test.entity.TestAttempt;
import com.smartlearnly.backend.test.entity.TestQuestion;
import com.smartlearnly.backend.test.repository.StudentTestAnswerRepository;
import com.smartlearnly.backend.test.repository.TestAttemptRepository;
import com.smartlearnly.backend.test.repository.TestQuestionRepository;
import com.smartlearnly.backend.test.repository.TestRepository;
import com.smartlearnly.backend.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TestAttemptService {

    private final TestAttemptRepository repository;
    private final TestRepository testRepository;
    private final TestQuestionRepository testQuestionRepository;
    private final QuestionAnswerRepository questionAnswerRepository;
    private final StudentTestAnswerRepository studentTestAnswerRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final TestService testService;
    private final UserRepository userRepository;

    @Transactional
    public TestAttemptModel.Response startAttempt(TestAttemptModel.StartRequest request) {
        Test test = testRepository.findById(required(request.getTestId(), "testId"))
                .orElseThrow(() -> new EntityNotFoundException("Test not found"));
        UUID studentId = required(request.getStudentId(), "studentId");
        if (!testService.accessCodeMatches(test, request.getAccessCode())) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Invalid or expired test access code");
        }

        List<TestAttempt> existingAttempts =
                repository.findByTestIdAndStudentIdOrderByStartTimeDesc(test.getId(), studentId);
        if (!existingAttempts.isEmpty()) {
            TestAttempt latest = expireIfOverdue(existingAttempts.get(0));
            if (isActive(latest.getStatus())) {
                return mapToResponse(latest);
            }
            if (!Boolean.TRUE.equals(latest.getRetakeAllowed())) {
                return mapToResponse(latest);
            }
            latest.setRetakeAllowed(false);
            repository.save(latest);
        }

        Instant start = Instant.now();
        TestAttempt attempt = new TestAttempt();
        attempt.setTestId(test.getId());
        attempt.setStudentId(studentId);
        attempt.setAssignmentId(request.getAssignmentId());
        attempt.setStartTime(start);
        attempt.setEndTime(start.plus(Duration.ofMinutes(resolveDuration(test))));
        attempt.setStatus(AttemptStatus.DOING);

        TestAttempt saved = repository.save(attempt);
        TestAttemptModel.Response response = mapToResponse(saved);
        broadcast(response, request.getStudentName());
        return response;
    }

    @Transactional
    public TestAttemptModel.Response submitAttempt(UUID id, TestAttemptModel.SubmitRequest request) {
        TestAttempt attempt = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Attempt not found"));

        if (attempt.getStatus() == AttemptStatus.SUBMITTED
                || attempt.getStatus() == AttemptStatus.GRADED
                || attempt.getStatus() == AttemptStatus.EXPIRED) {
            return mapToResponse(attempt);
        }

        Instant now = Instant.now();
        boolean expired = attempt.getEndTime() != null && now.isAfter(attempt.getEndTime());
        GradeResult grade = gradeAttempt(attempt);
        attempt.setScore(grade.score());
        attempt.setStatus(expired ? AttemptStatus.EXPIRED : AttemptStatus.SUBMITTED);

        TestAttempt updated = repository.save(attempt);
        TestAttemptModel.Response response = mapToResponse(updated);
        response.setPercentage(grade.percentage());
        broadcast(response, null);
        return response;
    }

    @Transactional
    public List<TestAttemptModel.Response> getAttempts(UUID testId, UUID studentId) {
        return repository.findByTestIdAndStudentIdOrderByStartTimeDesc(testId, studentId)
                .stream()
                .map(this::expireIfOverdue)
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional
    public List<TestAttemptModel.Response> getAttemptsByTest(UUID testId) {
        return repository.findByTestIdOrderByStartTimeAsc(testId)
                .stream()
                .map(this::expireIfOverdue)
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional
    public void reopenAttempt(UUID testId, UUID studentId) {
        List<TestAttempt> attempts = repository.findByTestIdAndStudentIdOrderByStartTimeDesc(testId, studentId);
        if (attempts.isEmpty()) {
            return;
        }
        TestAttempt latest = expireIfOverdue(attempts.get(0));
        latest.setRetakeAllowed(true);
        repository.save(latest);

        MonitorEvent event = new MonitorEvent();
        event.setTargetId(testId);
        event.setStudentId(studentId);
        event.setType("mcq");
        event.setStatus("REOPENED");
        messagingTemplate.convertAndSend("/topic/tests/monitor/" + testId, event);
        messagingTemplate.convertAndSend("/topic/tests/monitor", event);
    }

    private GradeResult gradeAttempt(TestAttempt attempt) {
        List<TestQuestion> testQuestions =
                testQuestionRepository.findByIdTestId(attempt.getTestId());
        List<StudentTestAnswer> answers =
                studentTestAnswerRepository.findByAttemptId(attempt.getId());

        BigDecimal total = testQuestions.stream()
                .map(TestQuestion::getMarks)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal score = BigDecimal.ZERO;

        for (StudentTestAnswer answer : answers) {
            boolean correct = answer.getSelectedAnswerId() != null
                    && questionAnswerRepository.findById(answer.getSelectedAnswerId())
                            .map(QuestionAnswer::getIsCorrect)
                            .orElse(false);
            BigDecimal marks = testQuestions.stream()
                    .filter(item -> item.getId().getQuestionId().equals(answer.getQuestionId()))
                    .findFirst()
                    .map(TestQuestion::getMarks)
                    .orElse(BigDecimal.ZERO);
            answer.setIsCorrect(correct);
            answer.setScoreAwarded(correct ? marks : BigDecimal.ZERO);
            if (correct) {
                score = score.add(marks);
            }
        }
        studentTestAnswerRepository.saveAll(answers);

        BigDecimal percentage = total.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : score.multiply(BigDecimal.valueOf(100))
                        .divide(total, 2, RoundingMode.HALF_UP);
        return new GradeResult(score, percentage);
    }

    private TestAttempt expireIfOverdue(TestAttempt attempt) {
        if (!isActive(attempt.getStatus())
                || attempt.getEndTime() == null
                || !Instant.now().isAfter(attempt.getEndTime())) {
            return attempt;
        }

        GradeResult grade = gradeAttempt(attempt);
        attempt.setScore(grade.score());
        attempt.setStatus(AttemptStatus.EXPIRED);
        return repository.save(attempt);
    }

    private boolean isActive(AttemptStatus status) {
        return status == AttemptStatus.DOING || status == AttemptStatus.IN_PROGRESS;
    }

    private void broadcast(TestAttemptModel.Response response, String studentName) {
        MonitorEvent event = new MonitorEvent();
        event.setTargetId(response.getTestId());
        event.setAttemptId(response.getId());
        event.setStudentId(response.getStudentId());
        event.setStudentName(studentName != null ? studentName : response.getStudentName());
        event.setType("mcq");
        event.setStatus(response.getStatus().name());
        event.setStartTime(response.getStartTime());
        event.setEndTime(response.getEndTime());
        event.setScore(response.getScore());
        event.setPercentage(response.getPercentage());
        event.setRemainingSeconds(remainingSeconds(response.getEndTime()));
        messagingTemplate.convertAndSend("/topic/tests/monitor/" + response.getTestId(), event);
        messagingTemplate.convertAndSend("/topic/tests/monitor", event);
    }

    private TestAttemptModel.Response mapToResponse(TestAttempt attempt) {
        TestAttemptModel.Response response = new TestAttemptModel.Response();
        response.setId(attempt.getId());
        response.setTestId(attempt.getTestId());
        response.setStudentId(attempt.getStudentId());
        response.setStudentName(resolveStudentName(attempt.getStudentId()));
        response.setStartTime(attempt.getStartTime());
        response.setEndTime(attempt.getEndTime());
        response.setScore(attempt.getScore());
        response.setPercentage(null);
        response.setStatus(attempt.getStatus());
        response.setCreatedAt(attempt.getCreatedAt());
        response.setAssignmentId(attempt.getAssignmentId());
        response.setRetakeAllowed(Boolean.TRUE.equals(attempt.getRetakeAllowed()));
        return response;
    }

    private String resolveStudentName(UUID studentId) {
        if (studentId == null) {
            return null;
        }
        return userRepository.findByIdAndDeletedAtIsNull(studentId)
                .map(user -> user.getFullName() == null || user.getFullName().isBlank()
                        ? user.getEmail()
                        : user.getFullName())
                .orElse(null);
    }

    private Integer resolveDuration(Test test) {
        return test.getDurationMinutes() == null || test.getDurationMinutes() <= 0
                ? 30
                : test.getDurationMinutes();
    }

    private Long remainingSeconds(Instant endTime) {
        if (endTime == null) {
            return null;
        }
        return Math.max(0, Duration.between(Instant.now(), endTime).getSeconds());
    }

    private UUID required(UUID value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private record GradeResult(BigDecimal score, BigDecimal percentage) {
    }
}
