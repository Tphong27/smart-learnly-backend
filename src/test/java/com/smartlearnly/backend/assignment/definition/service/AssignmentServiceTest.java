package com.smartlearnly.backend.assignment.definition.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.assignment.definition.dto.AssignmentModel;
import com.smartlearnly.backend.assignment.entity.Assignment;
import com.smartlearnly.backend.assignment.repository.AssignmentRepository;
import com.smartlearnly.backend.assignment.repository.AssignmentSubmissionRepository;
import com.smartlearnly.backend.classroom.repository.ClassOfferingRepository;
import com.smartlearnly.backend.classroom.entity.ClassOffering;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.curriculum.entity.CurriculumLesson;
import com.smartlearnly.backend.curriculum.entity.CurriculumVersion;
import com.smartlearnly.backend.curriculum.repository.CurriculumLessonRepository;
import com.smartlearnly.backend.curriculum.repository.CurriculumVersionRepository;
import com.smartlearnly.backend.curriculum.service.ClassCurriculumCompositionService;
import com.smartlearnly.backend.curriculum.service.CurriculumResolutionService;
import com.smartlearnly.backend.curriculum.service.CurriculumResolution;
import com.smartlearnly.backend.user.entity.UserAccount;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AssignmentServiceTest {
    @Mock
    private AssignmentRepository assignmentRepository;
    @Mock
    private ClassOfferingRepository classOfferingRepository;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private AssignmentSubmissionRepository assignmentSubmissionRepository;
    @Mock
    private CurriculumResolutionService curriculumResolutionService;
    @Mock
    private CurriculumLessonRepository curriculumLessonRepository;
    @Mock
    private CurriculumVersionRepository curriculumVersionRepository;
    @Mock
    private ClassCurriculumCompositionService compositionService;

    private AssignmentService service;

    @BeforeEach
    void setUp() {
        service = new AssignmentService(
                assignmentRepository,
                classOfferingRepository,
                currentUserService,
                assignmentSubmissionRepository,
                curriculumResolutionService,
                curriculumLessonRepository,
                curriculumVersionRepository,
                compositionService);
    }

    @Test
    void createAssignmentShouldLinkMasterLessonToCourse() {
        UUID lessonId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UserAccount admin = new UserAccount();
        admin.setId(UUID.randomUUID());
        admin.setRole("ADMIN");
        CurriculumLesson lesson = new CurriculumLesson();
        lesson.setId(lessonId);
        lesson.setCurriculumVersionId(versionId);
        CurriculumVersion version = new CurriculumVersion();
        version.setId(versionId);
        version.setCourseId(courseId);
        AssignmentModel.CreateRequest request = new AssignmentModel.CreateRequest();
        request.setLessonId(lessonId);
        request.setTitle("Final project");
        request.setDescription("Submit the completed project.");
        request.setAllowLateSubmission(false);
        request.setIsFlashtest(false);

        when(currentUserService.requireAuthenticatedUser()).thenReturn(admin);
        when(assignmentRepository.save(any(Assignment.class))).thenAnswer(invocation -> {
            Assignment saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            saved.setCreatedAt(Instant.now());
            saved.setUpdatedAt(Instant.now());
            return saved;
        });
        when(curriculumLessonRepository.findById(lessonId)).thenReturn(Optional.of(lesson));
        when(curriculumVersionRepository.findById(versionId)).thenReturn(Optional.of(version));

        AssignmentModel.Response response = service.createAssignment(request);

        assertThat(response.getLessonId()).isEqualTo(lessonId);
        assertThat(response.getCourseId()).isEqualTo(courseId);
        assertThat(response.getCreatedBy()).isEqualTo(admin.getId());
        assertThat(response.getTitle()).isEqualTo("Final project");
    }

    @Test
    void createAssignmentShouldTakeClassIdFromLessonCurriculum() {
        UUID lessonId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID classId = UUID.randomUUID();
        UserAccount trainer = new UserAccount();
        trainer.setId(UUID.randomUUID());
        trainer.setRole("TRAINER");
        ClassOffering classOffering = new ClassOffering();
        classOffering.setId(classId);
        classOffering.setCourseId(courseId);
        CurriculumVersion version = new CurriculumVersion();
        version.setId(versionId);
        version.setCourseId(courseId);
        version.setClassId(classId);
        CurriculumLesson lesson = new CurriculumLesson();
        lesson.setId(lessonId);
        lesson.setCurriculumVersionId(versionId);
        AssignmentModel.CreateRequest request = new AssignmentModel.CreateRequest();
        request.setLessonId(lessonId);
        request.setTitle("Class assignment");

        when(curriculumLessonRepository.findById(lessonId)).thenReturn(Optional.of(lesson));
        when(curriculumVersionRepository.findById(versionId)).thenReturn(Optional.of(version));
        when(classOfferingRepository.findByIdAndDeletedAtIsNull(classId))
                .thenReturn(Optional.of(classOffering));
        when(currentUserService.requireAuthenticatedUser()).thenReturn(trainer);
        when(assignmentRepository.save(any(Assignment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AssignmentModel.Response response = service.createAssignment(request);

        assertThat(response.getClassId()).isEqualTo(classId);
        verify(assignmentRepository).save(any(Assignment.class));
        verify(curriculumResolutionService, never()).resolveClassEffectivePublished(any(), any());
    }

    @Test
    void updateAssignmentShouldAttachLegacyAssignmentToTrainerClass() {
        UUID assignmentId = UUID.randomUUID();
        UUID classId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID lessonId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        Assignment assignment = new Assignment();
        assignment.setId(assignmentId);
        assignment.setLessonId(lessonId);
        assignment.setTitle("Daily assignment");
        ClassOffering classOffering = new ClassOffering();
        classOffering.setId(classId);
        classOffering.setCourseId(courseId);
        CurriculumVersion version = new CurriculumVersion();
        version.setId(versionId);
        version.setCourseId(courseId);
        CurriculumLesson lesson = new CurriculumLesson();
        lesson.setId(lessonId);
        lesson.setCurriculumVersionId(versionId);
        AssignmentModel.UpdateRequest request = new AssignmentModel.UpdateRequest();
        request.setClassId(classId);

        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(classOfferingRepository.findByIdAndDeletedAtIsNull(classId))
                .thenReturn(Optional.of(classOffering));
        when(curriculumResolutionService.resolveClassEffectivePublished(courseId, classId))
                .thenReturn(new CurriculumResolution(
                        version,
                        null,
                        classId,
                        false,
                        CurriculumResolutionService.SOURCE_MASTER_INHERITED));
        when(compositionService.resolveEffectiveLesson(any(CurriculumVersion.class), any(UUID.class)))
                .thenReturn(Optional.of(lesson));
        when(assignmentRepository.save(assignment)).thenReturn(assignment);

        AssignmentModel.Response response = service.updateAssignment(assignmentId, request);

        assertThat(assignment.getClassId()).isEqualTo(classId);
        assertThat(response.getClassId()).isEqualTo(classId);
    }

    @Test
    void updateAssignmentShouldAcceptDraftLessonEquivalentToPublishedLesson() {
        UUID assignmentId = UUID.randomUUID();
        UUID classId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID draftLessonId = UUID.randomUUID();
        UUID publishedLessonId = UUID.randomUUID();
        UUID lessonIdentityId = UUID.randomUUID();
        UUID publishedVersionId = UUID.randomUUID();
        Assignment assignment = new Assignment();
        assignment.setId(assignmentId);
        assignment.setLessonId(draftLessonId);
        assignment.setTitle("Daily assignment");
        ClassOffering classOffering = new ClassOffering();
        classOffering.setId(classId);
        classOffering.setCourseId(courseId);
        CurriculumVersion publishedVersion = new CurriculumVersion();
        publishedVersion.setId(publishedVersionId);
        publishedVersion.setCourseId(courseId);
        CurriculumLesson draftLesson = new CurriculumLesson();
        draftLesson.setId(draftLessonId);
        draftLesson.setLessonIdentityId(lessonIdentityId);
        CurriculumLesson publishedLesson = new CurriculumLesson();
        publishedLesson.setId(publishedLessonId);
        publishedLesson.setLessonIdentityId(lessonIdentityId);
        AssignmentModel.UpdateRequest request = new AssignmentModel.UpdateRequest();
        request.setClassId(classId);

        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(classOfferingRepository.findByIdAndDeletedAtIsNull(classId))
                .thenReturn(Optional.of(classOffering));
        when(curriculumResolutionService.resolveClassEffectivePublished(courseId, classId))
                .thenReturn(new CurriculumResolution(
                        publishedVersion,
                        null,
                        classId,
                        false,
                        CurriculumResolutionService.SOURCE_MASTER_INHERITED));
        when(curriculumLessonRepository.findById(draftLessonId))
                .thenReturn(Optional.of(draftLesson));
        when(curriculumLessonRepository.findAllByLessonIdentityId(lessonIdentityId))
                .thenReturn(List.of(draftLesson, publishedLesson));
        when(compositionService.resolveEffectiveLesson(
                any(CurriculumVersion.class),
                any(UUID.class)))
                .thenAnswer(invocation -> lessonIdentityId.equals(invocation.getArgument(1))
                        ? Optional.of(publishedLesson)
                        : Optional.empty());
        when(assignmentRepository.save(assignment)).thenReturn(assignment);

        AssignmentModel.Response response = service.updateAssignment(assignmentId, request);

        assertThat(assignment.getClassId()).isEqualTo(classId);
        assertThat(response.getClassId()).isEqualTo(classId);
    }

    @Test
    void deleteAssignmentShouldRemoveSubmissionsBeforeParent() {
        UUID assignmentId = UUID.randomUUID();
        Assignment assignment = new Assignment();
        assignment.setId(assignmentId);
        assignment.setTitle("Final project");

        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));

        service.deleteAssignment(assignmentId);

        var ordered = inOrder(assignmentSubmissionRepository, assignmentRepository);
        ordered.verify(assignmentSubmissionRepository).deleteByAssignmentId(assignmentId);
        ordered.verify(assignmentSubmissionRepository).flush();
        ordered.verify(assignmentRepository).deleteById(assignmentId);
    }

    @Test
    void deleteAssignmentShouldRejectUnknownAssignmentWithoutDeletingChildren() {
        UUID assignmentId = UUID.randomUUID();
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteAssignment(assignmentId))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Assignment not found");

        verify(assignmentSubmissionRepository, never()).deleteByAssignmentId(assignmentId);
        verify(assignmentRepository, never()).deleteById(assignmentId);
    }
}
