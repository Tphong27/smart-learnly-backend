package com.smartlearnly.backend.test.definition.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.classroom.repository.ClassOfferingRepository;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.course.access.service.CourseAccessService;
import com.smartlearnly.backend.curriculum.repository.CurriculumSectionRepository;
import com.smartlearnly.backend.test.definition.dto.TestModel;
import com.smartlearnly.backend.test.repository.StudentTestAnswerRepository;
import com.smartlearnly.backend.test.repository.TestAttemptRepository;
import com.smartlearnly.backend.test.repository.TestRepository;
import com.smartlearnly.backend.user.entity.UserAccount;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TestServiceClassScopeTest {

    @Mock
    private TestRepository testRepository;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private TestAttemptRepository testAttemptRepository;
    @Mock
    private StudentTestAnswerRepository studentTestAnswerRepository;
    @Mock
    private CurriculumSectionRepository curriculumSectionRepository;
    @Mock
    private ClassOfferingRepository classOfferingRepository;
    @Mock
    private CourseAccessService courseAccessService;

    @InjectMocks
    private TestService service;

    @Test
    void availableTestsAreLoadedFromTheTraineesEnrolledClasses() {
        UUID traineeId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID classId = UUID.randomUUID();
        UserAccount trainee = user(traineeId, "TRAINEE");
        com.smartlearnly.backend.test.entity.Test test = publishedTest(classId, courseId);

        when(currentUserService.requireAuthenticatedUser()).thenReturn(trainee);
        when(testRepository.findAvailableForStudent(
                traineeId,
                courseId,
                classId)).thenReturn(List.of(test));

        List<TestModel.Response> result = service.getAvailableTests(
                courseId,
                classId);

        assertThat(result).extracting(TestModel.Response::getId)
                .containsExactly(test.getId());
        assertThat(result.get(0).getAccessCode()).isNull();
    }

    @Test
    void trainerTestsIncludeTestsFromAssignedClasses() {
        UUID trainerId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID classId = UUID.randomUUID();
        UserAccount trainer = user(trainerId, "TRAINER");

        when(currentUserService.requireAuthenticatedUser()).thenReturn(trainer);
        when(testRepository.findStaffTests(trainerId, courseId, classId))
                .thenReturn(List.of());

        service.getMyTests(courseId, classId);

        verify(testRepository).findStaffTests(trainerId, courseId, classId);
    }

    @Test
    void privilegedStaffCanViewTheAdministrativeTestScope() {
        UUID tmoId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UserAccount tmo = user(tmoId, "TMO");

        when(currentUserService.requireAuthenticatedUser()).thenReturn(tmo);
        when(testRepository.findStaffTests(null, courseId, null))
                .thenReturn(List.of());

        service.getMyTests(courseId, null);

        verify(testRepository).findStaffTests(null, courseId, null);
    }

    @Test
    void legacyFlashTestIsNoLongerAccessibleById() {
        UUID testId = UUID.randomUUID();
        UserAccount admin = user(UUID.randomUUID(), "ADMIN");
        com.smartlearnly.backend.test.entity.Test test = publishedTest(
                UUID.randomUUID(), UUID.randomUUID());
        test.setId(testId);
        test.setIsFlashtest(true);

        when(testRepository.findById(testId)).thenReturn(Optional.of(test));
        when(currentUserService.requireAuthenticatedUser()).thenReturn(admin);

        assertThatThrownBy(() -> service.getTestById(testId))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Test not found");
    }

    @Test
    void updateTestIsBlockedWhileATraineeIsTakingIt() {
        UUID testId = UUID.randomUUID();
        UserAccount admin = user(UUID.randomUUID(), "ADMIN");
        com.smartlearnly.backend.test.entity.Test test = publishedTest(
                UUID.randomUUID(), UUID.randomUUID());
        test.setId(testId);
        TestModel.UpdateRequest request = new TestModel.UpdateRequest();
        request.setTitle("Updated title");

        when(testRepository.findById(testId)).thenReturn(Optional.of(test));
        when(currentUserService.requireAuthenticatedUser()).thenReturn(admin);
        when(testAttemptRepository.existsActiveByTestId(testId)).thenReturn(true);

        assertThatThrownBy(() -> service.updateTest(testId, request))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.BUSINESS_RULE_VIOLATION))
                .hasMessageContaining("Cannot update this test while a trainee is taking it");
        verify(testRepository, never()).save(test);
    }

    private UserAccount user(UUID id, String role) {
        UserAccount user = new UserAccount();
        user.setId(id);
        user.setRole(role);
        return user;
    }

    private com.smartlearnly.backend.test.entity.Test publishedTest(
            UUID classId,
            UUID courseId) {
        com.smartlearnly.backend.test.entity.Test test =
                new com.smartlearnly.backend.test.entity.Test();
        test.setId(UUID.randomUUID());
        test.setClassId(classId);
        test.setCourseId(courseId);
        test.setIsPublished(true);
        test.setIsArchived(false);
        return test;
    }
}
