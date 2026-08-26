package com.smartlearnly.backend.test.attempt.service;

import com.smartlearnly.backend.curriculum.repository.CurriculumLessonRepository;
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
        UUID studentId = testService.requireCurrentActorCanStartAttempt(
                test.getId(), request.getClassId());
        boolean staffPreview = !testService.isCurrentActorTrainee();

        // Trainee giữ schedule + access code; staff preview (TMO/SME/TRAINER) bỏ qua.
        if (!staffPreview) {
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
        }

        List<TestAttempt> existingAttempts = findAttemptsInContext(
                test.getId(), studentId, request.getClassId());
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
        attempt.setClassId(request.getClassId());
        attempt.setAssignmentId(request.getAssignmentId());
        attempt.setStartTime(start);
        attempt.setEndTime(start.plus(Duration.ofMinutes(resolveDuration(test))));
        attempt.setStatus(AttemptStatus.DOING);

        TestAttempt saved = repository.save(attempt);
        return mapToResponse(saved);
    }

    /** Nộp attempt và chấm các câu trắc nghiệm của course quiz. */
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
                attempt.getClassId());

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
        return findAttemptsInContext(testId, studentId, classId)
                .stream()
                .map(this::expireIfOverdue)
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
                .map(this::mapToResponse)
                .toList();
    }

    /** Trả chi tiết một attempt sau khi cập nhật trạng thái hết hạn và điểm cuối cùng. */
    public TestAttemptModel.Response getAttemptById(UUID attemptId) {
        return getAttemptById(attemptId, null);
    }

    @Transactional
    public TestAttemptModel.Response getAttemptById(UUID attemptId, UUID classId) {
        TestAttempt attempt = repository.findById(attemptId)
                .orElseThrow(() -> new EntityNotFoundException("Attempt not found"));
        testService.requireAttemptAccess(
                attempt.getTestId(), attempt.getStudentId(), attempt.getClassId());
        return mapToResponse(expireIfOverdue(attempt));
    }

    /** Lấy lịch sử attempt trong đúng context course trực tiếp hoặc một lớp cụ thể. */
    private List<TestAttempt> findAttemptsInContext(
            UUID testId,
            UUID studentId,
            UUID classId) {
        if (classId == null) {
            return repository.findByTestIdAndStudentIdAndClassIdIsNullOrderByStartTimeDesc(
                    testId,
                    studentId);
        }
        return repository.findByTestIdAndStudentIdAndClassIdOrderByStartTimeDesc(
                testId,
                studentId,
                classId);
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
        // Staff preview attempt: không phát notification enrollment metrics.
        if (!testService.isCurrentActorTrainee()) {
            return;
        }
        Test test = testRepository.findById(attempt.getTestId()).orElse(null);
        UUID testOwnerId = test == null ? null : test.getCreatedBy();
        String status = attempt.getStatus() == null ? null : attempt.getStatus().name();
        String title = attempt.getStatus() == AttemptStatus.EXPIRED
                ? "Course quiz attempt expired"
                : "Course quiz attempt submitted";
        notificationService.emit(new NotificationCreateCommand(
                attempt.getStudentId(),
                NotificationType.TEST,
                title,
                test == null
                        ? "Your course quiz attempt has been recorded."
                        : "Your quiz attempt for " + test.getTitle() + " has been recorded.",
                "TEST_ATTEMPT",
                attempt.getId(),
                "/course-quiz-attempts/" + attempt.getId(),
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
                            ? "A learner's course quiz attempt has been recorded."
                            : "A learner's quiz attempt for " + test.getTitle() + " has been recorded.",
                    "TEST_ATTEMPT",
                    attempt.getId(),
                    "/course-quiz-attempts/" + attempt.getId(),
                    attempt.getStudentId(),
                    "test-attempt:" + attempt.getId() + ":" + status + ":owner",
                    NotificationPayloads.of(
                            "testId", attempt.getTestId(),
                            "studentId", attempt.getStudentId(),
                            "status", status)));
        }
    }

    /** Chuyển entity attempt thành dữ liệu API, bao gồm tỷ lệ điểm và thông tin học viên. */
    private TestAttemptModel.Response mapToResponse(TestAttempt attempt) {
        TestAttemptModel.Response response = new TestAttemptModel.Response();
        response.setId(attempt.getId());
        response.setTestId(attempt.getTestId());
        response.setStudentId(attempt.getStudentId());
        response.setClassId(attempt.getClassId());
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
