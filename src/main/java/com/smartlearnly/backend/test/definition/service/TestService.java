
package com.smartlearnly.backend.test.definition.service;

import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.classroom.entity.ClassOffering;
import com.smartlearnly.backend.classroom.repository.ClassOfferingRepository;
import com.smartlearnly.backend.course.access.service.CourseAccessService;
import com.smartlearnly.backend.curriculum.repository.CurriculumSectionRepository;
import com.smartlearnly.backend.enrollment.repository.ClassEnrollmentRepository;
import com.smartlearnly.backend.enrollment.repository.CourseEnrollmentRepository;
import com.smartlearnly.backend.notification.dto.NotificationCreateCommand;
import com.smartlearnly.backend.notification.entity.NotificationType;
import com.smartlearnly.backend.notification.service.NotificationService;
import com.smartlearnly.backend.test.definition.dto.TestModel;
import com.smartlearnly.backend.test.entity.Test;
import com.smartlearnly.backend.test.repository.TestAttemptRepository;
import com.smartlearnly.backend.test.repository.TestRepository;
import com.smartlearnly.backend.user.entity.UserAccount;
import jakarta.persistence.EntityNotFoundException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TestService {

    private static final Duration ACCESS_CODE_TTL = Duration.ofMinutes(5);
    private static final SecureRandom ACCESS_CODE_RANDOM = new SecureRandom();

    private final TestRepository testRepository;
    private final TestAttemptRepository testAttemptRepository;
    private final CurrentUserService currentUserService;
    private final CurriculumSectionRepository curriculumSectionRepository;
    private final ClassOfferingRepository classOfferingRepository;
    private final CourseAccessService courseAccessService;
    private NotificationService notificationService;
    private ClassEnrollmentRepository notificationClassEnrollmentRepository;
    private CourseEnrollmentRepository notificationCourseEnrollmentRepository;

    /** Gắn các dependency notification khi module thông báo được bật. */
    @Autowired(required = false)
    void setNotificationDependencies(
            NotificationService notificationService,
            ClassEnrollmentRepository classEnrollmentRepository,
            CourseEnrollmentRepository courseEnrollmentRepository) {
        this.notificationService = notificationService;
        this.notificationClassEnrollmentRepository = classEnrollmentRepository;
        this.notificationCourseEnrollmentRepository = courseEnrollmentRepository;
    }

    /** Tạo đề mới sau khi kiểm tra lịch mở, lớp học và quyền người tạo. */
    public List<TestModel.Response> getManagedTests(UUID courseId, UUID classId) {
        UserAccount actor = currentUserService.requireAuthenticatedUser();
        return testRepository.findManagedTests(
                        actor.getId(),
                        courseId,
                        classId,
                        isPrivilegedStaff(actor))
                .stream()
                .map(test -> mapToResponse(ensureAccessCode(test), true))
                .toList();
    }

    public List<TestModel.Response> getAvailableTests(UUID courseId, UUID classId) {
        UserAccount actor = currentUserService.requireAuthenticatedUser();
        if (!isTrainee(actor)) {
            return getManagedTests(courseId, classId);
        }
        return testRepository.findAvailableTestsForStudent(actor.getId(), courseId, classId)
                .stream()
                .map(test -> mapToResponse(test, false))
                .toList();
    }

    public TestModel.Response createTest(
            TestModel.CreateRequest request) {

        validateSchedule(request.getOpensAt(), request.getClosesAt());
        UserAccount actor = currentUserService.requireAuthenticatedUser();
        validateClassScope(request.getClassId(), request.getCourseId(), actor);

        Test test = new Test();

        test.setModuleId(request.getModuleId());
        validateCurriculumSection(request.getCourseId(), request.getCurriculumSectionId());
        test.setCurriculumSectionId(request.getCurriculumSectionId());
        test.setClassId(request.getClassId());
        test.setCourseId(request.getCourseId());
        test.setTitle(request.getTitle());
        test.setDescription(request.getDescription());
        test.setTestType(request.getTestType());
        test.setDurationMinutes(
                request.getDurationMinutes());
        test.setMaxAttempts(
                request.getMaxAttempts());
        test.setPassScore(
                request.getPassScore());
        test.setShuffleQuestions(
                request.getShuffleQuestions());
        test.setShuffleAnswers(
                request.getShuffleAnswers());
        test.setShowAnswersAfter(
                request.getShowAnswersAfter());
        test.setIsPublished(request.getIsPublished());
        test.setIsFlashtest(false);
        test.setOpensAt(request.getOpensAt());
        test.setClosesAt(request.getClosesAt());
        ensureAccessCode(test);
        test.setCreatedBy(actor.getId());

        Test saved = testRepository.save(test);

        if (Boolean.TRUE.equals(saved.getIsPublished())) {
            emitTestNotificationToStudents(
                    saved,
                    "New course quiz available",
                    "A new course quiz is available: " + saved.getTitle() + ".",
                    "created");
        }

        return mapToResponse(saved);
    }

    public TestModel.Response updateTest(UUID id, TestModel.UpdateRequest request) {
        Test test = testRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Test not found"));
        UserAccount actor = currentUserService.requireAuthenticatedUser();
        requireManageAccess(test, actor);
        requireNoActiveAttempts(id);

        validateSchedule(request.getOpensAt(), request.getClosesAt());
        validateClassScope(request.getClassId(), request.getCourseId(), actor);
        validateCurriculumSection(request.getCourseId(), request.getCurriculumSectionId());
        test.setModuleId(request.getModuleId());
        test.setCurriculumSectionId(request.getCurriculumSectionId());
        test.setClassId(request.getClassId());
        test.setCourseId(request.getCourseId());
        test.setTitle(request.getTitle());
        test.setDescription(request.getDescription());
        test.setTestType(request.getTestType());
        test.setDurationMinutes(request.getDurationMinutes());
        test.setMaxAttempts(request.getMaxAttempts());
        test.setPassScore(request.getPassScore());
        test.setShuffleQuestions(request.getShuffleQuestions());
        test.setShuffleAnswers(request.getShuffleAnswers());
        test.setShowAnswersAfter(request.getShowAnswersAfter());
        test.setIsPublished(request.getIsPublished());
        test.setOpensAt(request.getOpensAt());
        test.setClosesAt(request.getClosesAt());
        return mapToResponse(testRepository.save(test));
    }

    /** Cập nhật thời lượng và cấp lại đúng số phút còn lại cho các attempt đang làm. */
    @Transactional
    public TestModel.Response updateDuration(UUID id, TestModel.DurationUpdateRequest request) {
        Test test = testRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Test not found"));
        requireManageAccess(test, currentUserService.requireAuthenticatedUser());

        Integer durationMinutes = request == null ? null : request.getDurationMinutes();
        if (durationMinutes == null || durationMinutes < 1) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Test duration must be at least 1 minute");
        }

        Instant newEndTime = Instant.now().plus(Duration.ofMinutes(durationMinutes));
        var activeAttempts = testAttemptRepository.findActiveByTestId(id);
        activeAttempts.forEach(attempt -> attempt.setEndTime(newEndTime));
        if (!activeAttempts.isEmpty()) {
            testAttemptRepository.saveAll(activeAttempts);
        }

        test.setDurationMinutes(durationMinutes);
        return mapToResponse(testRepository.save(test));
    }

    public void archiveTest(UUID id) {
        Test test = testRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Test not found"));
        requireManageAccess(test, currentUserService.requireAuthenticatedUser());
        test.setIsArchived(true);
        testRepository.save(test);
    }

    public TestModel.AccessCodeVerifyResponse verifyAccessCode(
            UUID id,
            TestModel.AccessCodeVerifyRequest request,
            UUID classId) {
        Test test = testRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Test not found"));
        requireTestAccess(test, currentUserService.requireAuthenticatedUser(), classId);
        Test current = ensureAccessCode(test);
        TestModel.AccessCodeVerifyResponse response = new TestModel.AccessCodeVerifyResponse();
        response.setValid(accessCodeMatches(current, request == null ? null : request.getAccessCode()));
        response.setExpiresAt(current.getAccessCodeExpiresAt());
        return response;
    }

    /** Xác thực học viên hiện tại có quyền bắt đầu attempt của đề. */
    public UUID requireCurrentTraineeAccess(UUID testId) {
        return requireCurrentTraineeAccess(testId, null);
    }

    /** Xác thực quyền bắt đầu attempt trong context lớp cụ thể của học viên. */
    public UUID requireCurrentTraineeAccess(UUID testId, UUID classId) {
        UserAccount actor = currentUserService.requireAuthenticatedUser();
        if (!isTrainee(actor)) {
            throw new BusinessException(
                    ErrorCode.FORBIDDEN,
                    "Only trainees can start a course quiz attempt");
        }

        Test test = testRepository.findById(testId)
                .orElseThrow(() -> new EntityNotFoundException("Test not found"));
        requireTestAccess(test, actor, classId);
        return actor.getId();
    }

    /** Xác thực caller được xem hoặc thao tác attempt của học viên tương ứng. */
    public void requireAttemptAccess(UUID testId, UUID studentId) {
        requireAttemptAccess(testId, studentId, null);
    }

    /** Xác thực quyền xem attempt trong context lớp cụ thể của học viên. */
    public void requireAttemptAccess(UUID testId, UUID studentId, UUID classId) {
        UserAccount actor = currentUserService.requireAuthenticatedUser();
        Test test = testRepository.findById(testId)
                .orElseThrow(() -> new EntityNotFoundException("Test not found"));

        if (isTrainee(actor)) {
            if (!actor.getId().equals(studentId)) {
                throw new BusinessException(
                        ErrorCode.FORBIDDEN,
                        "You cannot access another trainee's attempt");
            }
            requireTestAccess(test, actor, classId);
            return;
        }

        requireManageAccess(test, actor);
    }

    /** Xác thực caller hiện tại có quyền quản lý đề trước thao tác quản trị. */
    public void requireCurrentUserCanManage(UUID testId) {
        Test test = testRepository.findById(testId)
                .orElseThrow(() -> new EntityNotFoundException("Test not found"));
        requireManageAccess(test, currentUserService.requireAuthenticatedUser());
    }

    /** Chặn chỉnh sửa đề khi học viên đang làm bài để giữ nguyên nội dung attempt. */
    public void requireNoActiveAttempts(UUID testId) {
        if (testAttemptRepository.existsActiveByTestId(testId)) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Cannot update this test while a trainee is taking it");
        }
    }

    /** Chặn thay đổi cấu trúc câu hỏi khi đề đã có lịch sử làm bài. */
    public void requireNoAttempts(UUID testId) {
        if (testAttemptRepository.existsByTestId(testId)) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Cannot change test questions after trainees have started this test");
        }
    }

    /** Trả chi tiết đề nếu caller có quyền; chỉ quản lý mới nhận mã truy cập. */
    public TestModel.Response getTestById(UUID id) {
        return getTestById(id, null);
    }

    /** Trả chi tiết đề theo context lớp để hỗ trợ quiz được kế thừa từ course. */
    public TestModel.Response getTestById(UUID id, UUID classId) {

        Test test = testRepository.findById(id)
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "Test not found"));

        UserAccount actor = currentUserService.requireAuthenticatedUser();
        requireTestAccess(test, actor, classId);
        boolean includeAccessCode = canManageTests(actor);
        return mapToResponse(
                includeAccessCode ? ensureAccessCode(test) : test,
                includeAccessCode);
    }

    /** Gửi notification về đề cho học viên ghi danh trong lớp hoặc course của đề. */
    private void emitTestNotificationToStudents(Test test, String title, String body, String eventSuffix) {
        if (notificationService == null || test == null) {
            return;
        }
        List<UUID> studentIds = List.of();
        if (test.getClassId() != null && notificationClassEnrollmentRepository != null) {
            studentIds = notificationClassEnrollmentRepository.findActiveOrCompletedStudentIdsByClassId(test.getClassId());
        } else if (test.getCourseId() != null && notificationCourseEnrollmentRepository != null) {
            studentIds = notificationCourseEnrollmentRepository.findActiveOrCompletedStudentIdsByCourseId(test.getCourseId());
        }
        for (UUID studentId : studentIds) {
            notificationService.emit(new NotificationCreateCommand(
                    studentId,
                    NotificationType.TEST,
                    title,
                    body,
                    "TEST",
                    test.getId(),
                    "/course-quizzes/" + test.getId(),
                    test.getCreatedBy(),
                    "test:" + test.getId() + ":" + eventSuffix,
                    notificationPayload(
                            "testId", test.getId(),
                            "courseId", test.getCourseId(),
                            "classId", test.getClassId(),
                            "title", test.getTitle())));
        }
    }

    /** Tạo payload notification chỉ giữ các cặp key/value có dữ liệu. */
    private Map<String, Object> notificationPayload(Object... keyValues) {
        Map<String, Object> payload = new LinkedHashMap<>();
        for (int index = 0; index + 1 < keyValues.length; index += 2) {
            Object value = keyValues[index + 1];
            if (value != null) {
                payload.put(String.valueOf(keyValues[index]), value);
            }
        }
        return payload;
    }

    /** Chuyển entity đề sang DTO quản trị với mã truy cập được phép hiển thị. */
    private TestModel.Response mapToResponse(
            Test test) {

        return mapToResponse(test, true);
    }

    /** Chuyển entity đề sang DTO và tùy chọn che mã truy cập khỏi học viên. */
    private TestModel.Response mapToResponse(
            Test test,
            boolean includeAccessCode) {

        TestModel.Response response =
                new TestModel.Response();

        response.setId(test.getId());
        response.setModuleId(test.getModuleId());
        response.setCurriculumSectionId(test.getCurriculumSectionId());
        response.setClassId(test.getClassId());
        response.setCourseId(test.getCourseId());
        response.setTitle(test.getTitle());
        response.setDescription(
                test.getDescription());
        response.setTestType(
                test.getTestType());
        response.setDurationMinutes(
                test.getDurationMinutes());
        response.setMaxAttempts(
                test.getMaxAttempts());
        response.setPassScore(
                test.getPassScore());
        response.setShuffleQuestions(
                test.getShuffleQuestions());
        response.setShuffleAnswers(
                test.getShuffleAnswers());
        response.setShowAnswersAfter(
                test.getShowAnswersAfter());
        response.setIsPublished(
                test.getIsPublished());
        response.setIsArchived(
                test.getIsArchived());
        response.setCreatedBy(
                test.getCreatedBy());
        response.setCreatedAt(
                test.getCreatedAt());
        response.setUpdatedAt(
                test.getUpdatedAt());
        if (includeAccessCode) {
            response.setAccessCode(test.getAccessCode());
            response.setAccessCodeExpiresAt(test.getAccessCodeExpiresAt());
        }
        response.setOpensAt(test.getOpensAt());
        response.setClosesAt(test.getClosesAt());
        response.setHasAttempts(test.getId() != null && testAttemptRepository.existsByTestId(test.getId()));
        response.setHasActiveAttempts(
                test.getId() != null && testAttemptRepository.existsActiveByTestId(test.getId()));

        return response;
    }

    /** Kiểm tra role có thuộc nhóm được quản lý đề hay không. */
    private boolean canManageTests(UserAccount actor) {
        String role = actor.getRole();
        return role != null && switch (role.toUpperCase()) {
            case "ADMIN", "TMO", "SME", "TRAINER" -> true;
            default -> false;
        };
    }

    /** Kiểm tra role có quyền quản trị toàn cục với đề hay không. */
    private boolean isPrivilegedStaff(UserAccount actor) {
        String role = actor.getRole();
        return role != null && switch (role.toUpperCase()) {
            case "ADMIN", "TMO", "SME" -> true;
            default -> false;
        };
    }

    /** Kiểm tra tài khoản hiện tại có role học viên hay không. */
    private boolean isTrainee(UserAccount actor) {
        return actor.getRole() != null && "TRAINEE".equalsIgnoreCase(actor.getRole());
    }

    /** Bảo vệ quyền xem đề theo enrollment học viên hoặc phân công nhân sự. */
    private void requireTestAccess(Test test, UserAccount actor) {
        requireTestAccess(test, actor, null);
    }

    /** Tách quyền quiz course trực tiếp khỏi quiz trong đúng lớp đang học. */
    private void requireTestAccess(Test test, UserAccount actor, UUID contextClassId) {
        if (Boolean.TRUE.equals(test.getIsFlashtest())) {
            throw new EntityNotFoundException("Test not found");
        }
        if (isTrainee(actor)) {
            boolean hasAccess = contextClassId == null
                    ? test.getClassId() == null
                    ? testRepository.existsAvailableCourseTestForStudent(
                    test.getId(), actor.getId())
                    : testRepository.existsAvailableForStudent(test.getId(), actor.getId())
                    : contextClassId.equals(test.getClassId())
                    ? testRepository.existsAvailableForStudent(test.getId(), actor.getId())
                    : test.getClassId() == null
                    && testRepository.existsAvailableCourseTestForStudentClass(
                    test.getId(), actor.getId(), contextClassId);
            if (hasAccess) {
                return;
            }
        } else if (isPrivilegedStaff(actor)
                || (canManageTests(actor)
                && testRepository.existsManagedByStaff(test.getId(), actor.getId()))) {
            return;
        }

        throw new BusinessException(
                ErrorCode.FORBIDDEN,
                "This test does not belong to one of your classes");
    }

    /** Bảo vệ quyền chỉnh sửa/xóa đề theo quyền toàn cục hoặc lớp được phân công. */
    private void requireManageAccess(Test test, UserAccount actor) {
        if (Boolean.TRUE.equals(test.getIsFlashtest())) {
            throw new EntityNotFoundException("Test not found");
        }
        if (isPrivilegedStaff(actor)
                || (canManageTests(actor)
                && testRepository.existsManagedByStaff(test.getId(), actor.getId()))) {
            return;
        }

        throw new BusinessException(
                ErrorCode.FORBIDDEN,
                "You cannot manage tests outside your assigned classes");
    }

    /** Kiểm tra course và quyền quản lý; đề cấp lớp còn phải thuộc đúng lớp được phân công. */
    private void validateClassScope(
            UUID classId,
            UUID courseId,
            UserAccount actor) {
        if (courseId == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "A course is required for every test");
        }
        if (classId == null) {
            courseAccessService.requireUpdatableCourse(courseId);
            return;
        }

        ClassOffering classOffering = classOfferingRepository
                .findByIdAndDeletedAtIsNull(classId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Selected class was not found"));

        if (!courseId.equals(classOffering.getCourseId())) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Selected class does not belong to this course");
        }

        if (!isPrivilegedStaff(actor)
                && (!"TRAINER".equalsIgnoreCase(actor.getRole())
                || !actor.getId().equals(classOffering.getTrainerId()))) {
            throw new BusinessException(
                    ErrorCode.FORBIDDEN,
                    "You can only create tests for classes assigned to you");
        }
    }

    /** Tạo hoặc làm mới mã truy cập ngắn hạn khi đề chưa có mã hợp lệ. */
    private Test ensureAccessCode(Test test) {
        Instant now = Instant.now();
        if (test.getAccessCode() == null ||
                test.getAccessCodeExpiresAt() == null ||
                !test.getAccessCodeExpiresAt().isAfter(now)) {
            test.setAccessCode(generateAccessCode());
            test.setAccessCodeExpiresAt(now.plus(ACCESS_CODE_TTL));
            return test.getId() == null ? test : testRepository.save(test);
        }
        return test;
    }

    /** Kiểm tra mã người học nhập khớp với mã truy cập còn hạn của đề. */
    public boolean accessCodeMatches(Test test, String accessCode) {
        Test current = ensureAccessCode(test);
        String expected = current.getAccessCode();
        return expected != null &&
                expected.equals(String.valueOf(accessCode == null ? "" : accessCode).trim());
    }

    /** Kiểm tra thời điểm hiện tại nằm trong cửa sổ mở đề. */
    public boolean isWithinSchedule(Test test, Instant now) {
        return (test.getOpensAt() == null || !now.isBefore(test.getOpensAt())) &&
                (test.getClosesAt() == null || now.isBefore(test.getClosesAt()));
    }

    /** Bảo đảm thời điểm đóng đề nằm sau thời điểm mở đề. */
    private void validateSchedule(Instant opensAt, Instant closesAt) {
        if (opensAt != null && closesAt != null && !opensAt.isBefore(closesAt)) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Test closing time must be after its opening time");
        }
    }

    /** Kiểm tra section curriculum được gắn vào đề thuộc đúng course. */
    private void validateCurriculumSection(UUID courseId, UUID sectionId) {
        if (sectionId == null) {
            return;
        }
        if (!curriculumSectionRepository.existsById(sectionId)) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Selected module was not found");
        }
        if (courseId == null || !curriculumSectionRepository.existsByIdAndCourseId(sectionId, courseId)) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Selected module does not belong to this course");
        }
    }

    /** Sinh mã truy cập sáu chữ số ngẫu nhiên cho đề. */
    private String generateAccessCode() {
        return String.format("%06d", ACCESS_CODE_RANDOM.nextInt(1_000_000));
    }
}

