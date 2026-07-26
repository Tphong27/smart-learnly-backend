package com.smartlearnly.backend.assignment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.assignment.dto.AssignmentModel;
import com.smartlearnly.backend.assignment.entity.Assignment;
import com.smartlearnly.backend.assignment.repository.AssignmentRepository;
import com.smartlearnly.backend.assignment.repository.AssignmentSubmissionRepository;
import com.smartlearnly.backend.classroom.repository.ClassOfferingRepository;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.curriculum.entity.CurriculumLesson;
import com.smartlearnly.backend.curriculum.entity.CurriculumVersion;
import com.smartlearnly.backend.curriculum.repository.CurriculumLessonRepository;
import com.smartlearnly.backend.curriculum.repository.CurriculumVersionRepository;
import com.smartlearnly.backend.curriculum.service.CurriculumResolutionService;
import com.smartlearnly.backend.user.entity.UserAccount;
import java.time.Instant;
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
                curriculumVersionRepository);
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
    void deleteAssignmentShouldRemoveSubmissionsBeforeParent() {
        UUID assignmentId = UUID.randomUUID();
        when(assignmentRepository.existsById(assignmentId)).thenReturn(true);

        service.deleteAssignment(assignmentId);

        var ordered = inOrder(assignmentSubmissionRepository, assignmentRepository);
        ordered.verify(assignmentSubmissionRepository).deleteByAssignmentId(assignmentId);
        ordered.verify(assignmentSubmissionRepository).flush();
        ordered.verify(assignmentRepository).deleteById(assignmentId);
    }

    @Test
    void deleteAssignmentShouldRejectUnknownAssignmentWithoutDeletingChildren() {
        UUID assignmentId = UUID.randomUUID();
        when(assignmentRepository.existsById(assignmentId)).thenReturn(false);

        assertThatThrownBy(() -> service.deleteAssignment(assignmentId))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Assignment not found");

        verify(assignmentSubmissionRepository, never()).deleteByAssignmentId(assignmentId);
        verify(assignmentRepository, never()).deleteById(assignmentId);
    }
}
