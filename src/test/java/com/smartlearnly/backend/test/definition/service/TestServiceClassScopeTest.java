package com.smartlearnly.backend.test.definition.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.classroom.entity.ClassOffering;
import com.smartlearnly.backend.classroom.repository.ClassOfferingRepository;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.course.access.service.CourseAccessService;
import com.smartlearnly.backend.curriculum.repository.CurriculumSectionRepository;
import com.smartlearnly.backend.test.definition.dto.TestModel;
import com.smartlearnly.backend.test.entity.AttemptStatus;
import com.smartlearnly.backend.test.entity.TestAttempt;
import com.smartlearnly.backend.test.repository.TestAttemptRepository;
import com.smartlearnly.backend.test.repository.TestRepository;
import com.smartlearnly.backend.user.entity.UserAccount;
import java.time.Duration;
import java.time.Instant;
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
    private TestAttemptRepository testAttemptRepository;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private CurriculumSectionRepository curriculumSectionRepository;
    @Mock
    private ClassOfferingRepository classOfferingRepository;
    @Mock
    private CourseAccessService courseAccessService;

    @InjectMocks
    private TestService service;

    @Test
    void directCourseContextShouldNotUseClassEnrollmentAccess() {
        UUID testId = UUID.randomUUID();
        UUID traineeId = UUID.randomUUID();
        UserAccount trainee = user(traineeId, "TRAINEE");
        com.smartlearnly.backend.test.entity.Test test = publishedTest(
                null, UUID.randomUUID());
        test.setId(testId);

        when(currentUserService.requireAuthenticatedUser()).thenReturn(trainee);
        when(testRepository.findById(testId)).thenReturn(Optional.of(test));
        when(testRepository.existsAvailableCourseTestForStudent(testId, traineeId))
                .thenReturn(true);

        assertThat(service.requireCurrentTraineeAccess(testId, null)).isEqualTo(traineeId);

        verify(testRepository, never())
                .existsAvailableCourseTestForStudentClass(testId, traineeId, null);
        verify(testRepository, never()).existsAvailableForStudent(testId, traineeId);
    }

    @Test
    void classContextShouldNotFallBackToDirectCourseEnrollment() {
        UUID testId = UUID.randomUUID();
        UUID traineeId = UUID.randomUUID();
        UUID classId = UUID.randomUUID();
        UserAccount trainee = user(traineeId, "TRAINEE");
        com.smartlearnly.backend.test.entity.Test test = publishedTest(
                null, UUID.randomUUID());
        test.setId(testId);

        when(currentUserService.requireAuthenticatedUser()).thenReturn(trainee);
        when(testRepository.findById(testId)).thenReturn(Optional.of(test));
        when(testRepository.existsAvailableCourseTestForStudentClass(
                testId, traineeId, classId)).thenReturn(false);

        assertThatThrownBy(() -> service.requireCurrentTraineeAccess(testId, classId))
                .isInstanceOf(com.smartlearnly.backend.common.exception.BusinessException.class)
                .hasMessage("This test does not belong to one of your classes");

        verify(testRepository, never())
                .existsAvailableCourseTestForStudent(testId, traineeId);
    }

    @Test
    void classSpecificTestShouldRequireItsMatchingClassContext() {
        UUID testId = UUID.randomUUID();
        UUID traineeId = UUID.randomUUID();
        UUID classId = UUID.randomUUID();
        UserAccount trainee = user(traineeId, "TRAINEE");
        com.smartlearnly.backend.test.entity.Test test = publishedTest(
                classId, UUID.randomUUID());
        test.setId(testId);

        when(currentUserService.requireAuthenticatedUser()).thenReturn(trainee);
        when(testRepository.findById(testId)).thenReturn(Optional.of(test));
        when(testRepository.existsAvailableForStudent(testId, traineeId)).thenReturn(true);

        assertThat(service.requireCurrentTraineeAccess(testId, classId)).isEqualTo(traineeId);
        assertThatThrownBy(() -> service.requireCurrentTraineeAccess(testId, UUID.randomUUID()))
                .isInstanceOf(com.smartlearnly.backend.common.exception.BusinessException.class);
    }

    @Test
    void legacyFlashTestIsNoLongerAccessibleById() {
        UUID testId = UUID.randomUUID();
        UserAccount tmo = user(UUID.randomUUID(), "TMO");
        com.smartlearnly.backend.test.entity.Test test = publishedTest(
                UUID.randomUUID(), UUID.randomUUID());
        test.setId(testId);
        test.setIsFlashtest(true);

        when(testRepository.findById(testId)).thenReturn(Optional.of(test));
        when(currentUserService.requireAuthenticatedUser()).thenReturn(tmo);

        assertThatThrownBy(() -> service.getTestById(testId))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Test not found");
    }

    @Test
    void updateTestIsBlockedWhileATraineeIsTakingIt() {
        UUID testId = UUID.randomUUID();
        UserAccount tmo = user(UUID.randomUUID(), "TMO");
        com.smartlearnly.backend.test.entity.Test test = publishedTest(
                UUID.randomUUID(), UUID.randomUUID());
        test.setId(testId);
        TestModel.UpdateRequest request = new TestModel.UpdateRequest();
        request.setTitle("Updated title");

        when(testRepository.findById(testId)).thenReturn(Optional.of(test));
        when(currentUserService.requireAuthenticatedUser()).thenReturn(tmo);
        when(testAttemptRepository.existsActiveByTestId(testId)).thenReturn(true);

        assertThatThrownBy(() -> service.updateTest(testId, request))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.BUSINESS_RULE_VIOLATION))
                .hasMessageContaining("Cannot update this test while a trainee is taking it");
        verify(testRepository, never()).save(test);
    }

    @Test
    void updateTestShouldKeepExistingBooleanFlagsWhenRequestOmitsThem() {
        UUID testId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID classId = UUID.randomUUID();
        UserAccount tmo = user(UUID.randomUUID(), "TMO");
        com.smartlearnly.backend.test.entity.Test test = publishedTest(classId, courseId);
        test.setId(testId);
        test.setTitle("Original title");
        test.setDescription("Original description");
        test.setShuffleQuestions(false);
        test.setShuffleAnswers(true);
        test.setShowAnswersAfter(true);

        TestModel.UpdateRequest request = new TestModel.UpdateRequest();
        request.setTitle("Updated title");
        request.setDescription("");
        request.setCourseId(courseId);
        request.setClassId(classId);
        request.setDurationMinutes(15);
        request.setMaxAttempts(1);
        request.setIsPublished(true);

        when(testRepository.findById(testId)).thenReturn(Optional.of(test));
        when(currentUserService.requireAuthenticatedUser()).thenReturn(tmo);
        when(classOfferingRepository.findByIdAndDeletedAtIsNull(classId))
                .thenReturn(Optional.of(classOffering(classId, courseId, UUID.randomUUID())));
        when(testRepository.save(test)).thenReturn(test);

        service.updateTest(testId, request);

        assertThat(test.getShuffleQuestions()).isFalse();
        assertThat(test.getShuffleAnswers()).isTrue();
        assertThat(test.getShowAnswersAfter()).isTrue();
        verify(testRepository).save(test);
    }

    @Test
    void updateDurationGivesActiveAttemptsOneMinuteFromUpdateTime() {
        UUID testId = UUID.randomUUID();
        UserAccount tmo = user(UUID.randomUUID(), "TMO");
        com.smartlearnly.backend.test.entity.Test test = publishedTest(
                UUID.randomUUID(), UUID.randomUUID());
        test.setId(testId);
        test.setDurationMinutes(30);

        TestAttempt activeAttempt = new TestAttempt();
        activeAttempt.setTestId(testId);
        activeAttempt.setStatus(AttemptStatus.DOING);
        activeAttempt.setEndTime(Instant.now().plus(Duration.ofMinutes(10)));

        TestModel.DurationUpdateRequest request = new TestModel.DurationUpdateRequest();
        request.setDurationMinutes(1);
        Instant beforeUpdate = Instant.now();

        when(testRepository.findById(testId)).thenReturn(Optional.of(test));
        when(currentUserService.requireAuthenticatedUser()).thenReturn(tmo);
        when(testAttemptRepository.findActiveByTestId(testId)).thenReturn(List.of(activeAttempt));
        when(testRepository.save(test)).thenReturn(test);
        when(testAttemptRepository.existsActiveByTestId(testId)).thenReturn(true);

        TestModel.Response response = service.updateDuration(testId, request);

        Instant afterUpdate = Instant.now();
        assertThat(test.getDurationMinutes()).isEqualTo(1);
        assertThat(activeAttempt.getEndTime())
                .isBetween(
                        beforeUpdate.plus(Duration.ofMinutes(1)),
                        afterUpdate.plus(Duration.ofMinutes(1)));
        assertThat(response.getHasActiveAttempts()).isTrue();
        verify(testAttemptRepository).saveAll(List.of(activeAttempt));
        verify(testRepository).save(test);
    }

    private ClassOffering classOffering(UUID classId, UUID courseId, UUID trainerId) {
        ClassOffering classOffering = new ClassOffering();
        classOffering.setId(classId);
        classOffering.setCourseId(courseId);
        classOffering.setTrainerId(trainerId);
        return classOffering;
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
