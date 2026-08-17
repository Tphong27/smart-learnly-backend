package com.smartlearnly.backend.test.definition.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.classroom.entity.ClassOffering;
import com.smartlearnly.backend.classroom.repository.ClassOfferingRepository;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.course.access.service.CourseAccessService;
import com.smartlearnly.backend.curriculum.repository.CurriculumSectionRepository;
import com.smartlearnly.backend.test.definition.dto.TestModel;
import com.smartlearnly.backend.test.entity.TestType;
import com.smartlearnly.backend.test.repository.TestAttemptRepository;
import com.smartlearnly.backend.test.repository.TestRepository;
import com.smartlearnly.backend.user.entity.UserAccount;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TestServiceCreateTestTest {

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

    private TestService service;

    @BeforeEach
    void setUp() {
        service = new TestService(
                testRepository,
                testAttemptRepository,
                currentUserService,
                curriculumSectionRepository,
                classOfferingRepository,
                courseAccessService);
    }

    @Test
    void createTestShouldPersistClassScopedPublishedTestWithAccessCode() {
        UUID trainerId = UUID.randomUUID();
        UUID classId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID sectionId = UUID.randomUUID();
        UserAccount trainer = user(trainerId, "TRAINER");
        ClassOffering classOffering = classOffering(classId, courseId, trainerId);
        TestModel.CreateRequest request = createRequest(classId, courseId);
        request.setCurriculumSectionId(sectionId);
        request.setIsPublished(true);

        when(currentUserService.requireAuthenticatedUser()).thenReturn(trainer);
        when(classOfferingRepository.findByIdAndDeletedAtIsNull(classId))
                .thenReturn(java.util.Optional.of(classOffering));
        when(curriculumSectionRepository.existsById(sectionId)).thenReturn(true);
        when(curriculumSectionRepository.existsByIdAndCourseId(sectionId, courseId)).thenReturn(true);
        when(testRepository.save(any(com.smartlearnly.backend.test.entity.Test.class))).thenAnswer(invocation -> {
            com.smartlearnly.backend.test.entity.Test saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            saved.setCreatedAt(Instant.now());
            saved.setUpdatedAt(Instant.now());
            return saved;
        });

        TestModel.Response response = service.createTest(request);

        assertThat(response.getClassId()).isEqualTo(classId);
        assertThat(response.getCourseId()).isEqualTo(courseId);
        assertThat(response.getCurriculumSectionId()).isEqualTo(sectionId);
        assertThat(response.getCreatedBy()).isEqualTo(trainerId);
        assertThat(response.getAccessCode()).hasSize(6).containsOnlyDigits();
        assertThat(response.getAccessCodeExpiresAt()).isAfter(Instant.now());
        ArgumentCaptor<com.smartlearnly.backend.test.entity.Test> testCaptor =
                ArgumentCaptor.forClass(com.smartlearnly.backend.test.entity.Test.class);
        verify(testRepository).save(testCaptor.capture());
        assertThat(testCaptor.getValue().getTitle()).isEqualTo("Midterm quiz");
        assertThat(testCaptor.getValue().getPassScore()).isEqualByComparingTo("7.5");
    }

    @Test
    void createTestShouldRejectInvalidScheduleBeforeSaving() {
        TestModel.CreateRequest request = createRequest(UUID.randomUUID(), UUID.randomUUID());
        Instant opensAt = Instant.parse("2026-08-05T10:00:00Z");
        request.setOpensAt(opensAt);
        request.setClosesAt(opensAt);

        assertThatThrownBy(() -> service.createTest(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Test closing time must be after its opening time");

        verify(testRepository, never()).save(any(com.smartlearnly.backend.test.entity.Test.class));
        verify(classOfferingRepository, never()).findByIdAndDeletedAtIsNull(any());
    }

    @Test
    void createTestShouldRejectTrainerCreatingForUnassignedClass() {
        UUID trainerId = UUID.randomUUID();
        UUID classId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UserAccount trainer = user(trainerId, "TRAINER");
        ClassOffering classOffering = classOffering(classId, courseId, UUID.randomUUID());
        TestModel.CreateRequest request = createRequest(classId, courseId);

        when(currentUserService.requireAuthenticatedUser()).thenReturn(trainer);
        when(classOfferingRepository.findByIdAndDeletedAtIsNull(classId))
                .thenReturn(java.util.Optional.of(classOffering));

        assertThatThrownBy(() -> service.createTest(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("You can only create tests for classes assigned to you");

        verify(testRepository, never()).save(any(com.smartlearnly.backend.test.entity.Test.class));
    }

    @Test
    void createTestShouldPersistCourseScopedTestWhenClassIdIsMissing() {
        UUID staffId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        TestModel.CreateRequest request = createRequest(null, courseId);

        when(currentUserService.requireAuthenticatedUser()).thenReturn(user(staffId, "SME"));
        when(testRepository.save(any(com.smartlearnly.backend.test.entity.Test.class))).thenAnswer(invocation -> {
            com.smartlearnly.backend.test.entity.Test saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            saved.setCreatedAt(Instant.now());
            saved.setUpdatedAt(Instant.now());
            return saved;
        });

        TestModel.Response response = service.createTest(request);

        assertThat(response.getClassId()).isNull();
        assertThat(response.getCourseId()).isEqualTo(courseId);
        verify(courseAccessService).requireUpdatableCourse(courseId);
    }

    @Test
    void createTestShouldRejectWhenCourseIdIsMissing() {
        TestModel.CreateRequest request = createRequest(UUID.randomUUID(), null);

        assertThatThrownBy(() -> service.createTest(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("A course is required for every test");

        verify(testRepository, never()).save(any(com.smartlearnly.backend.test.entity.Test.class));
    }

    @Test
    void createTestShouldRejectWhenClassBelongsToAnotherCourse() {
        UUID adminId = UUID.randomUUID();
        UUID classId = UUID.randomUUID();
        UUID requestedCourseId = UUID.randomUUID();
        ClassOffering classOffering = classOffering(classId, UUID.randomUUID(), UUID.randomUUID());
        TestModel.CreateRequest request = createRequest(classId, requestedCourseId);

        when(currentUserService.requireAuthenticatedUser()).thenReturn(user(adminId, "ADMIN"));
        when(classOfferingRepository.findByIdAndDeletedAtIsNull(classId))
                .thenReturn(java.util.Optional.of(classOffering));

        assertThatThrownBy(() -> service.createTest(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Selected class does not belong to this course");

        verify(testRepository, never()).save(any(com.smartlearnly.backend.test.entity.Test.class));
    }

    @Test
    void createTestShouldRejectUnknownCurriculumSection() {
        UUID adminId = UUID.randomUUID();
        UUID classId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID sectionId = UUID.randomUUID();
        TestModel.CreateRequest request = createRequest(classId, courseId);
        request.setCurriculumSectionId(sectionId);

        when(currentUserService.requireAuthenticatedUser()).thenReturn(user(adminId, "ADMIN"));
        when(classOfferingRepository.findByIdAndDeletedAtIsNull(classId))
                .thenReturn(java.util.Optional.of(classOffering(classId, courseId, UUID.randomUUID())));
        when(curriculumSectionRepository.existsById(sectionId)).thenReturn(false);

        assertThatThrownBy(() -> service.createTest(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Selected module was not found");

        verify(testRepository, never()).save(any(com.smartlearnly.backend.test.entity.Test.class));
    }

    private TestModel.CreateRequest createRequest(UUID classId, UUID courseId) {
        TestModel.CreateRequest request = new TestModel.CreateRequest();
        request.setClassId(classId);
        request.setCourseId(courseId);
        request.setTitle("Midterm quiz");
        request.setDescription("Covers the first three lessons.");
        request.setTestType(TestType.practice);
        request.setDurationMinutes(45);
        request.setMaxAttempts(1);
        request.setPassScore(new BigDecimal("7.5"));
        request.setShuffleQuestions(true);
        request.setShuffleAnswers(true);
        request.setShowAnswersAfter(false);
        request.setIsPublished(false);
        request.setOpensAt(Instant.parse("2026-08-05T09:00:00Z"));
        request.setClosesAt(Instant.parse("2026-08-05T10:00:00Z"));
        return request;
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
}
