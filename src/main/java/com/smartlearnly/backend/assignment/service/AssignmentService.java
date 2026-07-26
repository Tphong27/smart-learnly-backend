
package com.smartlearnly.backend.assignment.service;

import com.smartlearnly.backend.assignment.dto.AssignmentModel;
import com.smartlearnly.backend.assignment.entity.Assignment;
import com.smartlearnly.backend.assignment.repository.AssignmentRepository;
import com.smartlearnly.backend.assignment.repository.AssignmentSubmissionRepository;
import com.smartlearnly.backend.classroom.entity.ClassOffering;
import com.smartlearnly.backend.classroom.repository.ClassAdminProjection;
import com.smartlearnly.backend.classroom.repository.ClassOfferingRepository;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.curriculum.service.CurriculumResolution;
import com.smartlearnly.backend.curriculum.service.CurriculumResolutionService;
import com.smartlearnly.backend.curriculum.entity.CurriculumLesson;
import com.smartlearnly.backend.curriculum.repository.CurriculumLessonRepository;
import com.smartlearnly.backend.curriculum.repository.CurriculumVersionRepository;
import com.smartlearnly.backend.user.entity.UserAccount;
import jakarta.persistence.EntityNotFoundException;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
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

    public AssignmentModel.Response createAssignment(
            AssignmentModel.CreateRequest request) {

        validateLessonForClass(request.getClassId(), request.getLessonId());

        Assignment assignment = new Assignment();

        assignment.setClassId(request.getClassId());
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
        assignment.setIsFlashtest(request.getIsFlashtest());
        assignment.setCreatedBy(currentUserService.requireAuthenticatedUser().getId());

        Assignment saved = assignmentRepository.save(assignment);

        return mapToResponse(saved);
    }

    public List<AssignmentModel.Response> getAllAssignments() {

        return assignmentRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    public List<AssignmentModel.Response> getMyAssignments(UUID courseId, Boolean isFlashtest) {
        UserAccount actor = currentUserService.requireAuthenticatedUser();
        return assignmentRepository.findStaffAssignments(actor.getId(), courseId, isFlashtest)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    // public List<AssignmentModel.Response> getAvailableAssignments(UUID courseId,
    // Boolean isFlashtest) {
    // UserAccount actor = currentUserService.requireAuthenticatedUser();
    // return assignmentRepository.findAvailableForStudent(actor.getId(), courseId,
    // isFlashtest)
    // .stream()
    // .map(this::mapToResponse)
    // .toList();
    // }

    public List<AssignmentModel.Response> getAvailableAssignments(UUID courseId, UUID classId, Boolean isFlashtest) {
        UserAccount actor = currentUserService.requireAuthenticatedUser();

        return assignmentRepository.findAvailableForStudent(actor.getId(), courseId, classId, isFlashtest)
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
                    .filter(assignment -> reference.equals(assignment.getLessonId()))
                    .max(newestFirst);
            if (match.isPresent()) {
                return match;
            }
        }

        return Optional.empty();
    }

    @Transactional
    public AssignmentModel.Response updateAssignment(
            UUID id,
            AssignmentModel.UpdateRequest request) {

        Assignment assignment = assignmentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Assignment not found"));

        if (request.getTitle() != null)
            assignment.setTitle(request.getTitle());
        if (request.getLessonId() != null) {
            validateLessonForClass(assignment.getClassId(), request.getLessonId());
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
        if (request.getIsFlashtest() != null)
            assignment.setIsFlashtest(request.getIsFlashtest());

        Assignment updated = assignmentRepository.save(assignment);
        if (Boolean.TRUE.equals(updated.getIsFlashtest())) {
            assignmentSubmissionRepository.deleteByAssignmentId(updated.getId());
        }

        return mapToResponse(updated);
    }

    @Transactional
    public void deleteAssignment(UUID id) {

        if (!assignmentRepository.existsById(id)) {
            throw new EntityNotFoundException("Assignment not found");
        }

        // A completed assignment owns submission rows. Delete those children
        // first so the supported staff delete action works after grading too.
        assignmentSubmissionRepository.deleteByAssignmentId(id);
        assignmentSubmissionRepository.flush();
        assignmentRepository.deleteById(id);
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
        response.setIsFlashtest(assignment.getIsFlashtest());
        response.setCreatedBy(assignment.getCreatedBy());
        response.setCreatedAt(assignment.getCreatedAt());
        response.setUpdatedAt(assignment.getUpdatedAt());
        response.setTestId(assignment.getTestId());

        return response;
    }

    private void validateLessonForClass(UUID classId, UUID lessonId) {
        if (classId == null || lessonId == null) {
            return;
        }

        ClassOffering classOffering = classOfferingRepository.findByIdAndDeletedAtIsNull(classId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Class was not found"));
        CurriculumResolution resolution = curriculumResolutionService.resolveClassEffectivePublished(
                classOffering.getCourseId(),
                classId);
        boolean lessonBelongsToClass = curriculumLessonRepository
                .findEffectiveLessonReference(resolution.version().getId(), lessonId)
                .isPresent();
        if (!lessonBelongsToClass) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Assignment lesson must belong to this class curriculum");
        }
    }

    private UUID resolveClassId(Assignment assignment) {
        if (assignment.getClassId() != null) {
            return assignment.getClassId();
        }
        if (assignment.getLessonId() == null) {
            return null;
        }
        return curriculumLessonRepository.findById(assignment.getLessonId())
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
        return "ADMIN".equalsIgnoreCase(role)
                || "TMO".equalsIgnoreCase(role)
                || "SME".equalsIgnoreCase(role);
    }
}
