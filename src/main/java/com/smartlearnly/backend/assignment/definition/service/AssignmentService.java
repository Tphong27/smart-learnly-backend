
package com.smartlearnly.backend.assignment.definition.service;

import com.smartlearnly.backend.assignment.definition.dto.AssignmentModel;
import com.smartlearnly.backend.assignment.entity.Assignment;
import com.smartlearnly.backend.assignment.repository.AssignmentRepository;
import com.smartlearnly.backend.assignment.repository.AssignmentSubmissionRepository;
import com.smartlearnly.backend.classroom.entity.ClassOffering;
import com.smartlearnly.backend.classroom.repository.ClassAdminProjection;
import com.smartlearnly.backend.classroom.repository.ClassOfferingRepository;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.curriculum.service.ClassCurriculumCompositionService;
import com.smartlearnly.backend.curriculum.service.CurriculumResolution;
import com.smartlearnly.backend.curriculum.service.CurriculumResolutionService;
import com.smartlearnly.backend.curriculum.entity.CurriculumLesson;
import com.smartlearnly.backend.curriculum.repository.CurriculumLessonRepository;
import com.smartlearnly.backend.curriculum.repository.CurriculumVersionRepository;
import com.smartlearnly.backend.enrollment.repository.ClassEnrollmentRepository;
import com.smartlearnly.backend.notification.dto.NotificationCreateCommand;
import com.smartlearnly.backend.notification.entity.NotificationType;
import com.smartlearnly.backend.notification.service.NotificationPayloads;
import com.smartlearnly.backend.notification.service.NotificationService;
import com.smartlearnly.backend.user.entity.UserAccount;
import jakarta.persistence.EntityNotFoundException;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AssignmentService {

    private final AssignmentRepository assignmentRepository;
    private final ClassOfferingRepository classOfferingRepository;
    private final CurrentUserService currentUserService;
    private final AssignmentSubmissionRepository assignmentSubmissionRepository;
    private final CurriculumResolutionService curriculumResolutionService;
    private final CurriculumLessonRepository curriculumLessonRepository;
    private final CurriculumVersionRepository curriculumVersionRepository;
    private final ClassCurriculumCompositionService compositionService;
    private NotificationService notificationService;
    private ClassEnrollmentRepository notificationClassEnrollmentRepository;

    @Autowired(required = false)
    void setNotificationDependencies(
            NotificationService notificationService,
            ClassEnrollmentRepository classEnrollmentRepository) {
        this.notificationService = notificationService;
        this.notificationClassEnrollmentRepository = classEnrollmentRepository;
    }

    public AssignmentModel.Response createAssignment(
            AssignmentModel.CreateRequest request) {

        UUID classId = resolveClassId(request.getClassId(), request.getLessonId());
        validateLessonForClass(classId, request.getLessonId());

        Assignment assignment = new Assignment();

        assignment.setClassId(classId);
        assignment.setLessonId(request.getLessonId());
        assignment.setTitle(request.getTitle());
        assignment.setDescription(request.getDescription());
        assignment.setRubric(request.getRubric());
        assignment.setInstructionFileUrl(request.getInstructionFileUrl());
        assignment.setInstructionFileName(request.getInstructionFileName());
        assignment.setDueDate(request.getDueDate());
        assignment.setAllowLateSubmission(request.getAllowLateSubmission());
        assignment.setLockoutDate(request.getLockoutDate());
        assignment.setMaxScore(request.getMaxScore());
        assignment.setTestId(request.getTestId());
        assignment.setIsFlashtest(false);
        assignment.setCreatedBy(currentUserService.requireAuthenticatedUser().getId());
        requireInstructionsPresent(
                assignment.getDescription(),
                assignment.getInstructionFileUrl());

        Assignment saved = assignmentRepository.save(assignment);

        emitAssignmentNotificationToStudents(
                saved,
                "New assignment",
                "A new assignment is available: " + saved.getTitle() + ".",
                "created");

        return mapToResponse(saved);
    }

    public List<AssignmentModel.Response> getAllAssignments() {

        return assignmentRepository.findAll()
                .stream()
                .filter(this::isSupportedAssignment)
                .map(this::mapToResponse)
                .toList();
    }

    /** Trả assignment mà staff hiện tại sở hữu hoặc được phân công quản lý. */
    public List<AssignmentModel.Response> getMyAssignments(UUID courseId) {
        UserAccount actor = currentUserService.requireAuthenticatedUser();
        return assignmentRepository.findStaffAssignments(actor.getId(), courseId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    /** Trả assignment khả dụng cho học viên theo course và lớp. */
    public List<AssignmentModel.Response> getAvailableAssignments(UUID courseId, UUID classId) {
        UserAccount actor = currentUserService.requireAuthenticatedUser();

        return assignmentRepository.findAvailableForStudent(actor.getId(), courseId, classId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    public List<AssignmentModel.ClassOptionResponse> getAssignableClasses(UUID courseId) {
        UserAccount actor = currentUserService.requireAuthenticatedUser();
        UUID trainerId = isAdminOrTmoOrSme(actor) ? null : actor.getId();
        return classOfferingRepository.findAssignableClasses(courseId, trainerId)
                .stream()
                .map(this::mapToClassOptionResponse)
                .toList();
    }

    public AssignmentModel.Response getAssignmentById(UUID id) {

        Assignment assignment = assignmentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Assignment not found"));
        requireSupportedAssignment(assignment);

        return mapToResponse(assignment);
    }

    public AssignmentModel.Response getAssignmentByLessonId(UUID lessonId) {
        return findAssignmentByLessonId(lessonId, null)
                .orElseThrow(() -> new EntityNotFoundException("Assignment not found"));
    }

    public Optional<AssignmentModel.Response> findAssignmentByLessonId(
            UUID lessonId,
            UUID classId) {
        if (lessonId == null) {
            return Optional.empty();
        }

        List<UUID> lessonReferences = resolveEquivalentLessonReferences(lessonId);

        if (classId != null) {
            Optional<Assignment> classAssignment = selectBestAssignment(
                    assignmentRepository.findByLessonIdInAndClassId(
                            lessonReferences,
                            classId),
                    lessonReferences);
            if (classAssignment.isPresent()) {
                return classAssignment.map(this::mapToResponse);
            }
        }

        Optional<Assignment> sharedAssignment = selectBestAssignment(
                assignmentRepository.findByLessonIdInAndClassIdIsNull(
                        lessonReferences),
                lessonReferences);
        if (sharedAssignment.isPresent()) {
            return sharedAssignment.map(this::mapToResponse);
        }

        if (classId == null) {
            return selectBestAssignment(
                    assignmentRepository.findByLessonIdIn(lessonReferences),
                    lessonReferences)
                    .map(this::mapToResponse);
        }

        return Optional.empty();
    }

    private List<UUID> resolveEquivalentLessonReferences(UUID lessonId) {
        Set<UUID> references = new LinkedHashSet<>();
        references.add(lessonId);

        curriculumLessonRepository.findById(lessonId).ifPresent(lesson -> {
            addLessonReferences(references, lesson);
            UUID lessonIdentityId = lesson.getLessonIdentityId();
            if (lessonIdentityId != null) {
                curriculumLessonRepository.findAllByLessonIdentityId(lessonIdentityId)
                        .forEach(equivalentLesson ->
                                addLessonReferences(references, equivalentLesson));
            }
        });

        return List.copyOf(references);
    }

    private void addLessonReferences(
            Set<UUID> references,
            CurriculumLesson lesson) {
        if (lesson.getId() != null) {
            references.add(lesson.getId());
        }
        if (lesson.getSourceCurriculumLessonId() != null) {
            references.add(lesson.getSourceCurriculumLessonId());
        }
        if (lesson.getSourceLessonId() != null) {
            references.add(lesson.getSourceLessonId());
        }
        if (lesson.getLessonIdentityId() != null) {
            references.add(lesson.getLessonIdentityId());
        }
    }

    private Optional<Assignment> selectBestAssignment(
            List<Assignment> assignments,
            List<UUID> lessonReferences) {
        if (assignments == null || assignments.isEmpty()) {
            return Optional.empty();
        }

        Comparator<Assignment> newestFirst = Comparator.comparing(
                Assignment::getUpdatedAt,
                Comparator.nullsFirst(Comparator.naturalOrder()));

        for (UUID reference : lessonReferences) {
            Optional<Assignment> match = assignments.stream()
                    .filter(this::isSupportedAssignment)
                    .filter(assignment -> reference.equals(assignment.getLessonId()))
                    .max(newestFirst);
            if (match.isPresent()) {
                return match;
            }
        }

        return Optional.empty();
    }

    /**
     * Cập nhật bài tập và cho phép gắn lại bài cũ vào đúng lớp.
     * Cặp class/lesson mới luôn được kiểm tra trước khi lưu để tránh giao nhầm curriculum.
     */
    @Transactional
    public AssignmentModel.Response updateAssignment(
            UUID id,
            AssignmentModel.UpdateRequest request) {

        Assignment assignment = assignmentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Assignment not found"));
        requireSupportedAssignment(assignment);
        requireNoActiveSubmissions(id);

        UUID targetClassId = request.getClassId() != null
                ? request.getClassId()
                : assignment.getClassId();
        UUID targetLessonId = request.getLessonId() != null
                ? request.getLessonId()
                : assignment.getLessonId();
        if (request.getClassId() != null || request.getLessonId() != null) {
            validateLessonForClass(targetClassId, targetLessonId);
        }

        if (request.getTitle() != null)
            assignment.setTitle(request.getTitle());
        if (request.getClassId() != null)
            assignment.setClassId(request.getClassId());
        if (request.getLessonId() != null) {
            assignment.setLessonId(request.getLessonId());
        }
        if (request.getDescription() != null)
            assignment.setDescription(request.getDescription());
        if (request.getRubric() != null)
            assignment.setRubric(request.getRubric());
        assignment.setInstructionFileUrl(request.getInstructionFileUrl());
        assignment.setInstructionFileName(request.getInstructionFileName());
        if (request.getDueDate() != null)
            assignment.setDueDate(request.getDueDate());
        if (request.getAllowLateSubmission() != null) {
            assignment.setAllowLateSubmission(request.getAllowLateSubmission());
        }
        if (request.getLockoutDate() != null)
            assignment.setLockoutDate(request.getLockoutDate());
        if (request.getMaxScore() != null)
            assignment.setMaxScore(request.getMaxScore());
        if (request.getIsArchived() != null)
            assignment.setIsArchived(request.getIsArchived());
        if (request.getTestId() != null)
            assignment.setTestId(request.getTestId());
        requireInstructionsPresent(
                assignment.getDescription(),
                assignment.getInstructionFileUrl());
        Assignment updated = assignmentRepository.save(assignment);

        emitAssignmentNotificationToStudents(
                updated,
                "Assignment updated",
                updated.getTitle() + " was updated.",
                "updated");

        return mapToResponse(updated);
    }

    @Transactional
    public void deleteAssignment(UUID id) {

        Assignment assignment = assignmentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Assignment not found"));
        requireSupportedAssignment(assignment);
        requireNoActiveSubmissions(id);
        emitAssignmentNotificationToStudents(
                assignment,
                "Assignment removed",
                assignment.getTitle() + " was removed.",
                "deleted");

        // A completed assignment owns submission rows. Delete those children
        // first so the supported staff delete action works after grading too.
        assignmentSubmissionRepository.deleteByAssignmentId(id);
        assignmentSubmissionRepository.flush();
        assignmentRepository.deleteById(id);
    }

    private void requireNoActiveSubmissions(UUID assignmentId) {
        if (assignmentSubmissionRepository.existsActiveByAssignmentId(assignmentId)) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Cannot update this assignment while a trainee is working on it");
        }
    }

    /**
     * Bắt buộc có instructions text (sau khi strip HTML) hoặc file đính kèm.
     * Cả hai trống → 400 Invalid request.
     */
    private void requireInstructionsPresent(String description, String instructionFileUrl) {
        boolean hasText = !isBlankHtml(description);
        boolean hasFile = instructionFileUrl != null && !instructionFileUrl.isBlank();
        if (!hasText && !hasFile) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Instructions text or file is required");
        }
    }

    /** HTML rỗng / chỉ thẻ trống / whitespace được coi là blank. */
    private boolean isBlankHtml(String html) {
        if (html == null || html.isBlank()) {
            return true;
        }
        String withoutTags = html
                .replaceAll("(?is)<script[^>]*>.*?</script>", " ")
                .replaceAll("(?is)<style[^>]*>.*?</style>", " ")
                .replaceAll("(?is)<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace(' ', ' ')
                .replaceAll("\\s+", " ")
                .trim();
        return withoutTags.isEmpty();
    }

    private void emitAssignmentNotificationToStudents(
            Assignment assignment,
            String title,
            String body,
            String eventSuffix) {
        if (notificationService == null
                || notificationClassEnrollmentRepository == null
                || assignment == null
                || assignment.getClassId() == null) {
            return;
        }
        for (UUID studentId : notificationClassEnrollmentRepository.findActiveOrCompletedStudentIdsByClassId(assignment.getClassId())) {
            notificationService.emit(new NotificationCreateCommand(
                    studentId,
                    NotificationType.ASSIGNMENT,
                    title,
                    body,
                    "ASSIGNMENT",
                    assignment.getId(),
                    "/assignments/" + assignment.getId(),
                    assignment.getCreatedBy(),
                    "assignment:" + assignment.getId() + ":" + eventSuffix,
                    NotificationPayloads.of(
                            "assignmentId", assignment.getId(),
                            "classId", assignment.getClassId(),
                            "title", assignment.getTitle())));
        }
    }

    private AssignmentModel.Response mapToResponse(Assignment assignment) {

        AssignmentModel.Response response = new AssignmentModel.Response();

        response.setId(assignment.getId());
        UUID classId = resolveClassId(assignment);
        response.setClassId(classId);
        response.setCourseId(resolveCourseId(classId, assignment));
        response.setLessonId(assignment.getLessonId());
        response.setTitle(assignment.getTitle());
        response.setDescription(assignment.getDescription());
        response.setRubric(assignment.getRubric());
        response.setInstructionFileUrl(
                assignment.getInstructionFileUrl());
        response.setInstructionFileName(
                assignment.getInstructionFileName());
        response.setDueDate(assignment.getDueDate());
        response.setAllowLateSubmission(
                assignment.getAllowLateSubmission());
        response.setLockoutDate(assignment.getLockoutDate());
        response.setMaxScore(assignment.getMaxScore());
        response.setIsArchived(assignment.getIsArchived());
        response.setCreatedBy(assignment.getCreatedBy());
        response.setCreatedAt(assignment.getCreatedAt());
        response.setUpdatedAt(assignment.getUpdatedAt());
        response.setTestId(assignment.getTestId());

        return response;
    }

    /** Nhận diện assignment thường; bản ghi legacy bị giữ lại chỉ để bảo toàn lịch sử. */
    private boolean isSupportedAssignment(Assignment assignment) {
        return assignment != null && !Boolean.TRUE.equals(assignment.getIsFlashtest());
    }

    /** Chặn truy cập nghiệp vụ tới assignment legacy đã ngừng hỗ trợ. */
    private void requireSupportedAssignment(Assignment assignment) {
        if (!isSupportedAssignment(assignment)) {
            throw new EntityNotFoundException("Assignment not found");
        }
    }

    /**
     * Kiểm tra lesson thuộc curriculum đang được Trainee học trong lớp.
     * Chấp nhận ID của bản draft/bản sao khi nó có cùng định danh với lesson đã publish.
     */
    private void validateLessonForClass(UUID classId, UUID lessonId) {
        if (classId == null || lessonId == null) {
            return;
        }

        ClassOffering classOffering = classOfferingRepository.findByIdAndDeletedAtIsNull(classId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Class was not found"));
        if (lessonBelongsDirectlyToClass(classOffering, classId, lessonId)) {
            return;
        }
        CurriculumResolution resolution = curriculumResolutionService.resolveClassEffectivePublished(
                classOffering.getCourseId(),
                classId);
        boolean lessonBelongsToClass = resolveEquivalentLessonReferences(lessonId)
                .stream()
                .anyMatch(reference -> compositionService
                        .resolveEffectiveLesson(resolution.version(), reference)
                        .isPresent());
        if (!lessonBelongsToClass) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Assignment lesson must belong to this class curriculum");
        }
    }

    /**
     * Chấp nhận lesson nằm trực tiếp trong một curriculum version của đúng lớp.
     * Trường hợp này bao gồm lesson mới trong draft, chưa có bản tương đương ở
     * curriculum published để cơ chế đối chiếu identity phía dưới tìm thấy.
     */
    private boolean lessonBelongsDirectlyToClass(
            ClassOffering classOffering,
            UUID classId,
            UUID lessonId) {
        return curriculumLessonRepository.findById(lessonId)
                .map(CurriculumLesson::getCurriculumVersionId)
                .flatMap(curriculumVersionRepository::findById)
                .filter(version -> classId.equals(version.getClassId()))
                .filter(version -> classOffering.getCourseId().equals(version.getCourseId()))
                .isPresent();
    }

    private UUID resolveClassId(Assignment assignment) {
        return resolveClassId(assignment.getClassId(), assignment.getLessonId());
    }

    /**
     * Lấy phạm vi lớp trực tiếp từ curriculum lesson khi client không gửi lại classId.
     * Lesson thuộc master curriculum vẫn giữ classId null; lesson của class draft/published
     * luôn kế thừa classId từ curriculum version chứa nó.
     */
    private UUID resolveClassId(UUID requestedClassId, UUID lessonId) {
        if (requestedClassId != null) {
            return requestedClassId;
        }
        if (lessonId == null) {
            return null;
        }
        return curriculumLessonRepository.findById(lessonId)
                .flatMap(lesson -> curriculumVersionRepository.findById(lesson.getCurriculumVersionId()))
                .map(version -> version.getClassId())
                .orElse(null);
    }

    private UUID resolveCourseId(UUID classId, Assignment assignment) {
        if (classId == null) {
            if (assignment.getLessonId() == null) {
                return null;
            }
            return curriculumLessonRepository.findById(assignment.getLessonId())
                    .flatMap(lesson -> curriculumVersionRepository.findById(lesson.getCurriculumVersionId()))
                    .map(version -> version.getCourseId())
                    .orElse(null);
        }
        return classOfferingRepository.findByIdAndDeletedAtIsNull(classId)
                .map(ClassOffering::getCourseId)
                .orElse(null);
    }

    private AssignmentModel.ClassOptionResponse mapToClassOptionResponse(ClassAdminProjection projection) {
        AssignmentModel.ClassOptionResponse response = new AssignmentModel.ClassOptionResponse();
        response.setId(projection.getId());
        response.setCourseId(projection.getCourseId());
        response.setCourseTitle(projection.getCourseTitle());
        response.setClassName(projection.getClassName());
        response.setTrainerId(projection.getTrainerId());
        response.setTrainerName(projection.getTrainerName());
        response.setStatus(projection.getStatus());
        response.setActiveEnrollmentCount(projection.getActiveEnrollmentCount());
        response.setMaxStudents(projection.getMaxStudents());
        return response;
    }

    private boolean isAdminOrTmoOrSme(UserAccount user) {
        String role = user.getRole();
        return "TMO".equalsIgnoreCase(role)
                || "SME".equalsIgnoreCase(role);
    }
}
