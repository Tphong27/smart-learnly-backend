package com.smartlearnly.backend.test.attempt.service;

import com.smartlearnly.backend.curriculum.repository.CurriculumLessonRepository;
import com.smartlearnly.backend.flashtest.dto.MonitorEvent;
import com.smartlearnly.backend.question.entity.QuestionAnswer;
import com.smartlearnly.backend.question.repository.QuestionAnswerRepository;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.notification.dto.NotificationCreateCommand;
import com.smartlearnly.backend.notification.entity.NotificationType;
import com.smartlearnly.backend.notification.service.NotificationPayloads;
import com.smartlearnly.backend.notification.service.NotificationService;
import com.smartlearnly.backend.test.attempt.dto.TestAttemptModel;
import com.smartlearnly.backend.test.entity.AttemptStatus;
import com.smartlearnly.backend.test.entity.StudentTestAnswer;
import com.smartlearnly.backend.test.entity.Test;
import com.smartlearnly.backend.test.entity.TestAttempt;
import com.smartlearnly.backend.test.entity.TestQuestion;
import com.smartlearnly.backend.test.repository.StudentTestAnswerRepository;
import com.smartlearnly.backend.test.repository.TestAttemptRepository;
import com.smartlearnly.backend.test.repository.TestQuestionRepository;
import com.smartlearnly.backend.test.repository.TestRepository;
import com.smartlearnly.backend.test.definition.service.TestService;
import com.smartlearnly.backend.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final CurriculumLessonRepository curriculumLessonRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final TestService testService;
    private final UserRepository userRepository;
    private NotificationService notificationService;

    /** Gắn notification service khi module thông báo được bật trong runtime. */
    @Autowired(required = false)
    void setNotificationService(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /** Bắt đầu attempt mới hoặc trả lại attempt đang làm theo chính sách làm lại của đề. */
    @Transactional
    public TestAttemptModel.Response startAttempt(TestAttemptModel.StartRequest request) {
        Test test = testRepository.findById(required(request.getTestId(), "testId"))
                .orElseThrow(() -> new EntityNotFoundException("Test not found"));
        UUID studentId = testService.requireCurrentTraineeAccess(test.getId(), request.getClassId());
        if (!testService.isWithinSchedule(test, Instant.now())) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    "This test is not open for attempts right now");
        }
        if (!isEmbeddedCourseQuiz(test)
                && !testService.accessCodeMatches(test, request.getAccessCode())) {
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

    /** Nộp attempt, chấm các câu trắc nghiệm và phát sự kiện theo dõi cho giảng viên. */
    private boolean isEmbeddedCourseQuiz(Test test) {
        return test != null
                && test.getId() != null
                && curriculumLessonRepository.existsByTestId(test.getId());
    }

    @Transactional
    public TestAttemptModel.Response submitAttempt(UUID id, TestAttemptModel.SubmitRequest request) {
        TestAttempt attempt = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Attempt not found"));
        testService.requireAttemptAccess(
                attempt.getTestId(),
                attempt.getStudentId(),
                request == null ? null : request.getClassId());

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
        emitAttemptCompletedNotification(updated);
        return response;
    }

    /** Trả lịch sử attempt của một học viên sau khi xác thực quyền xem. */
    @Transactional
    public List<TestAttemptModel.Response> getAttempts(UUID testId, UUID studentId) {
        return getAttempts(testId, studentId, null);
    }

    @Transactional
    public List<TestAttemptModel.Response> getAttempts(UUID testId, UUID studentId, UUID classId) {
        testService.requireAttemptAccess(testId, studentId, classId);
        return repository.findByTestIdAndStudentIdOrderByStartTimeDesc(testId, studentId)
                .stream()
                .map(this::expireIfOverdue)
                .map(this::refreshFinalGrade)
                .map(this::mapToResponse)
                .toList();
    }

    /** Trả tất cả attempt của đề cho người có quyền quản lý đề. */
    @Transactional
    public List<TestAttemptModel.Response> getAttemptsByTest(UUID testId) {
        testService.requireCurrentUserCanManage(testId);
        return repository.findByTestIdOrderByStartTimeAsc(testId)
                .stream()
                .map(this::expireIfOverdue)
                .map(this::refreshFinalGrade)
                .map(this::mapToResponse)
                .toList();
    }

    /** Trả chi tiết một attempt sau khi cập nhật trạng thái hết hạn và điểm cuối cùng. */
    @Transactional
    public TestAttemptModel.Response getAttemptById(UUID attemptId) {
        return getAttemptById(attemptId, null);
    }

    @Transactional
    public TestAttemptModel.Response getAttemptById(UUID attemptId, UUID classId) {
        TestAttempt attempt = repository.findById(attemptId)
                .orElseThrow(() -> new EntityNotFoundException("Attempt not found"));
        testService.requireAttemptAccess(attempt.getTestId(), attempt.getStudentId(), classId);
        return mapToResponse(refreshFinalGrade(expireIfOverdue(attempt)));
    }

    /** Cho phép học viên làm lại attempt gần nhất và thông báo realtime đến màn hình monitor. */
    @Transactional
    public void reopenAttempt(UUID testId, UUID studentId) {
        testService.requireCurrentUserCanManage(testId);
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

    /** Chấm các câu trắc nghiệm đã lưu của một attempt và đồng bộ điểm từng câu. */
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
                            .filter(selected -> selected.getQuestionId().equals(answer.getQuestionId()))
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

    /** Tính lại điểm cuối cho attempt đã nộp để phản ánh thay đổi chấm điểm thủ công. */
    private TestAttempt refreshFinalGrade(TestAttempt attempt) {
        if (attempt.getStatus() != AttemptStatus.SUBMITTED
                && attempt.getStatus() != AttemptStatus.GRADED
                && attempt.getStatus() != AttemptStatus.EXPIRED) {
            return attempt;
        }
        GradeResult grade = gradeAttempt(attempt);
        if (attempt.getScore() == null || attempt.getScore().compareTo(grade.score()) != 0) {
            attempt.setScore(grade.score());
            return repository.save(attempt);
        }
        return attempt;
    }

    /** Đánh dấu hết hạn và chấm attempt nếu người học vượt quá thời gian làm bài. */
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

    /** Kiểm tra attempt còn ở trạng thái người học có thể tiếp tục làm bài. */
    private boolean isActive(AttemptStatus status) {
        return status == AttemptStatus.DOING || status == AttemptStatus.IN_PROGRESS;
    }

    /** Gửi thông báo nộp/hết hạn attempt cho học viên và chủ đề khi notification khả dụng. */
    private void emitAttemptCompletedNotification(TestAttempt attempt) {
        if (notificationService == null
                || attempt == null
                || attempt.getStudentId() == null
                || attempt.getTestId() == null) {
            return;
        }
        Test test = testRepository.findById(attempt.getTestId()).orElse(null);
        UUID testOwnerId = test == null ? null : test.getCreatedBy();
        String status = attempt.getStatus() == null ? null : attempt.getStatus().name();
        String title = attempt.getStatus() == AttemptStatus.EXPIRED ? "Test attempt expired" : "Test attempt submitted";
        notificationService.emit(new NotificationCreateCommand(
                attempt.getStudentId(),
                NotificationType.TEST,
                title,
                test == null
                        ? "Your test attempt has been recorded."
                        : "Your attempt for " + test.getTitle() + " has been recorded.",
                "TEST_ATTEMPT",
                attempt.getId(),
                "/test-attempts/" + attempt.getId(),
                testOwnerId,
                "test-attempt:" + attempt.getId() + ":" + status + ":student",
                NotificationPayloads.of(
                        "testId", attempt.getTestId(),
                        "status", status)));
        if (testOwnerId != null && !testOwnerId.equals(attempt.getStudentId())) {
            notificationService.emit(new NotificationCreateCommand(
                    testOwnerId,
                    NotificationType.TEST,
                    title,
                    test == null
                            ? "A learner's test attempt has been recorded."
                            : "A learner's attempt for " + test.getTitle() + " has been recorded.",
                    "TEST_ATTEMPT",
                    attempt.getId(),
                    "/test-attempts/" + attempt.getId(),
                    attempt.getStudentId(),
                    "test-attempt:" + attempt.getId() + ":" + status + ":owner",
                    NotificationPayloads.of(
                            "testId", attempt.getTestId(),
                            "studentId", attempt.getStudentId(),
                            "status", status)));
        }
    }

    /** Phát sự kiện WebSocket để màn hình theo dõi cập nhật attempt theo thời gian thực. */
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

    /** Chuyển entity attempt thành dữ liệu API, bao gồm tỷ lệ điểm và thông tin học viên. */
    private TestAttemptModel.Response mapToResponse(TestAttempt attempt) {
        TestAttemptModel.Response response = new TestAttemptModel.Response();
        response.setId(attempt.getId());
        response.setTestId(attempt.getTestId());
        response.setStudentId(attempt.getStudentId());
        response.setStudentName(resolveStudentName(attempt.getStudentId()));
        response.setStartTime(attempt.getStartTime());
        response.setEndTime(attempt.getEndTime());
        response.setScore(attempt.getScore());
        List<TestQuestion> testQuestions = testQuestionRepository.findByIdTestId(attempt.getTestId());
        response.setTotalQuestions(testQuestions.size());
        BigDecimal totalMarks = testQuestions.stream()
                .map(TestQuestion::getMarks)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        response.setPercentage(attempt.getScore() == null || totalMarks.compareTo(BigDecimal.ZERO) == 0
                ? null
                : attempt.getScore()
                        .multiply(BigDecimal.valueOf(100))
                        .divide(totalMarks, 2, RoundingMode.HALF_UP));
        response.setStatus(attempt.getStatus());
        response.setCreatedAt(attempt.getCreatedAt());
        response.setAssignmentId(attempt.getAssignmentId());
        response.setRetakeAllowed(Boolean.TRUE.equals(attempt.getRetakeAllowed()));
        return response;
    }

    /** Lấy tên hiển thị của học viên cho dữ liệu monitor và lịch sử attempt. */
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

    /** Xác định thời lượng làm bài hợp lệ, dùng giá trị mặc định cho đề thiếu cấu hình. */
    private Integer resolveDuration(Test test) {
        return test.getDurationMinutes() == null || test.getDurationMinutes() <= 0
                ? 30
                : test.getDurationMinutes();
    }

    /** Tính số giây còn lại để client hiển thị đồng hồ đếm ngược. */
    private Long remainingSeconds(Instant endTime) {
        if (endTime == null) {
            return null;
        }
        return Math.max(0, Duration.between(Instant.now(), endTime).getSeconds());
    }

    /** Bảo đảm id bắt buộc đã có trước khi gọi repository. */
    private UUID required(UUID value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private record GradeResult(BigDecimal score, BigDecimal percentage) {
    }
}
