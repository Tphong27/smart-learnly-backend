package com.smartlearnly.backend.classroom.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.classroom.dto.ClassResponse;
import com.smartlearnly.backend.classroom.dto.CreateClassRequest;
import com.smartlearnly.backend.classroom.dto.UpdateClassRequest;
import com.smartlearnly.backend.classroom.entity.ClassOffering;
import com.smartlearnly.backend.classroom.repository.ClassOfferingRepository;
import com.smartlearnly.backend.common.audit.AuditLogService;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.course.entity.Course;
import com.smartlearnly.backend.course.repository.CourseRepository;
import com.smartlearnly.backend.enrollment.entity.EnrollmentStatus;
import com.smartlearnly.backend.enrollment.repository.ClassEnrollmentRepository;
import com.smartlearnly.backend.user.entity.UserAccount;
import com.smartlearnly.backend.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClassAdminServiceTest {
    @Mock
    private ClassOfferingRepository classOfferingRepository;
    @Mock
    private ClassEnrollmentRepository classEnrollmentRepository;
    @Mock
    private CourseRepository courseRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private AuditLogService auditLogService;

    private ClassAdminService service;

    @BeforeEach
    void setUp() {
        service = new ClassAdminService(
                classOfferingRepository,
                classEnrollmentRepository,
                courseRepository,
                userRepository,
                currentUserService,
                auditLogService
        );
    }

    @Test
    void createShouldPersistConfiguredClassAndAuditActor() {
        UserAccount actor = user("admin@smartlearnly.dev");
        UserAccount trainer = user("trainer@smartlearnly.dev");
        Course course = course();
        CreateClassRequest request = new CreateClassRequest(
                course.getId(),
                "Spring Cohort",
                trainer.getId(),
                "Mon/Wed 19:00",
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 8, 1),
                25,
                new BigDecimal("1500000")
        );
        when(currentUserService.requireAuthenticatedUser()).thenReturn(actor);
        when(courseRepository.findByIdAndDeletedAtIsNull(course.getId())).thenReturn(Optional.of(course));
        when(userRepository.findByIdAndRoleIgnoreCaseAndStatusIgnoreCaseAndDeletedAtIsNull(
                trainer.getId(),
                "TRAINER",
                "active"
        )).thenReturn(Optional.of(trainer));
        when(classOfferingRepository.save(any(ClassOffering.class))).thenAnswer(invocation -> {
            ClassOffering saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        ClassResponse response = service.create(request);

        assertThat(response.className()).isEqualTo("Spring Cohort");
        assertThat(response.maxStudents()).isEqualTo(25);
        assertThat(response.availableSeats()).isEqualTo(25);
        assertThat(response.price()).isEqualByComparingTo("1500000");
        assertThat(response.status()).isEqualTo("upcoming");
        verify(auditLogService).record(
                actor.getEmail(),
                "CLASS_CREATED",
                "CLASS",
                response.id().toString()
        );
    }

    @Test
    void updateShouldRejectCapacityBelowActiveEnrollmentCount() {
        ClassOffering classOffering = classOffering();
        UpdateClassRequest request = new UpdateClassRequest();
        request.setMaxStudents(2);
        when(classOfferingRepository.findByIdForUpdate(classOffering.getId()))
                .thenReturn(Optional.of(classOffering));
        when(classEnrollmentRepository.countByClassIdAndStatus(
                classOffering.getId(),
                EnrollmentStatus.ACTIVE
        )).thenReturn(3L);

        assertThatThrownBy(() -> service.update(classOffering.getId(), request))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.CLASS_CAPACITY_INVALID));

        verify(classOfferingRepository, never()).save(any());
    }

    @Test
    void updateShouldRejectExplicitNullCapacity() {
        ClassOffering classOffering = classOffering();
        UpdateClassRequest request = new UpdateClassRequest();
        request.setMaxStudents(null);
        when(classOfferingRepository.findByIdForUpdate(classOffering.getId()))
                .thenReturn(Optional.of(classOffering));

        assertThatThrownBy(() -> service.update(classOffering.getId(), request))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
    }

    @Test
    void updateShouldPreventCourseChangeAfterCommercialHistory() {
        ClassOffering classOffering = classOffering();
        UpdateClassRequest request = new UpdateClassRequest();
        request.setCourseId(UUID.randomUUID());
        when(classOfferingRepository.findByIdForUpdate(classOffering.getId()))
                .thenReturn(Optional.of(classOffering));
        when(classOfferingRepository.hasCommercialHistory(classOffering.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.update(classOffering.getId(), request))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT));

        verify(courseRepository, never()).findByIdAndDeletedAtIsNull(any());
    }

    private Course course() {
        Course course = new Course();
        course.setId(UUID.randomUUID());
        course.setTitle("Course title");
        return course;
    }

    private ClassOffering classOffering() {
        ClassOffering classOffering = new ClassOffering();
        classOffering.setId(UUID.randomUUID());
        classOffering.setCourseId(UUID.randomUUID());
        classOffering.setClassName("Existing class");
        classOffering.setMaxStudents(30);
        classOffering.setPrice(new BigDecimal("1500000"));
        classOffering.setStatus(com.smartlearnly.backend.classroom.entity.ClassStatus.UPCOMING);
        return classOffering;
    }

    private UserAccount user(String email) {
        UserAccount user = new UserAccount();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setFullName(email);
        return user;
    }
}
