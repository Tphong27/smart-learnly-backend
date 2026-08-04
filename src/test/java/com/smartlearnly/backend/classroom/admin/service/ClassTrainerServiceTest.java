package com.smartlearnly.backend.classroom.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.classroom.dto.ClassResponse;
import com.smartlearnly.backend.classroom.repository.ClassAdminProjection;
import com.smartlearnly.backend.classroom.repository.ClassOfferingRepository;
import com.smartlearnly.backend.common.api.PageResponse;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.common.security.CurrentUserService;
import com.smartlearnly.backend.user.entity.UserAccount;
import com.smartlearnly.backend.classroom.trainer.service.ClassTrainerService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class ClassTrainerServiceTest {

    private static final UUID TRAINER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CLASS_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID COURSE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID MISSING_CLASS_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");

    private static final LocalDate CLASS_START_DATE = LocalDate.of(2026, 8, 15);
    private static final LocalDate CLASS_END_DATE = LocalDate.of(2026, 9, 15);
    private static final Instant CREATED_AT = Instant.parse("2026-08-01T01:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-08-02T02:00:00Z");

    @Mock
    private ClassOfferingRepository classOfferingRepository;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private ClassAdminProjection projection;

    private ClassTrainerService service;
    private UserAccount trainer;

    @BeforeEach
    void setUp() {
        service = new ClassTrainerService(classOfferingRepository, currentUserService);

        trainer = new UserAccount();
        trainer.setId(TRAINER_ID);
        trainer.setEmail("trainer.nguyen@example.com");
        trainer.setFullName("Nguyen Van Trainer");
        trainer.setRole("TRAINER");
        trainer.setStatus("active");
    }

    // listMyAssignedClasses(): UTCID restarts from UTCID01.

    @Test
    void UTCID01_listMyAssignedClasses_normalizesFiltersAndCapsSize500To100() {
        stubProjection(30, 12L);
        PageImpl<ClassAdminProjection> repositoryPage = new PageImpl<>(
                List.of(projection),
                PageRequest.of(2, 100),
                201);

        when(currentUserService.requireAuthenticatedUser()).thenReturn(trainer);
        when(classOfferingRepository.findTrainerAssignedClasses(
                eq(TRAINER_ID),
                isNull(),
                eq("ongoing"),
                eq("%Java\\%\\_Backend\\\\2026%"),
                any(Pageable.class)))
                .thenReturn(repositoryPage);

        PageResponse<ClassResponse> result = service.listMyAssignedClasses(
                "  ONGOING  ",
                "  Java%_Backend\\2026  ",
                null,
                2,
                500);

        assertThat(result.page()).isEqualTo(2);
        assertThat(result.size()).isEqualTo(100);
        assertThat(result.totalItems()).isEqualTo(201);
        assertThat(result.totalPages()).isEqualTo(3);
        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(CLASS_ID);
            assertThat(item.courseId()).isEqualTo(COURSE_ID);
            assertThat(item.courseTitle()).isEqualTo("Java Backend Professional");
            assertThat(item.className()).isEqualTo("Java Backend Class 01");
            assertThat(item.trainerId()).isEqualTo(TRAINER_ID);
            assertThat(item.trainerName()).isEqualTo("Nguyen Van Trainer");
            assertThat(item.meetingUrl()).isEqualTo("https://meet.google.com/abc-defg-hij");
            assertThat(item.scheduleDescription()).isEqualTo(
                    "[{\"dayOfWeek\":\"MONDAY\",\"slotNumber\":2}]");
            assertThat(item.price()).isEqualByComparingTo("500000");
            assertThat(item.startDate()).isEqualTo(CLASS_START_DATE);
            assertThat(item.endDate()).isEqualTo(CLASS_END_DATE);
            assertThat(item.maxStudents()).isEqualTo(30);
            assertThat(item.activeEnrollmentCount()).isEqualTo(12);
            assertThat(item.availableSeats()).isEqualTo(18);
            assertThat(item.status()).isEqualTo("ongoing");
            assertThat(item.createdAt()).isEqualTo(CREATED_AT);
            assertThat(item.updatedAt()).isEqualTo(UPDATED_AT);
        });

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(classOfferingRepository).findTrainerAssignedClasses(
                eq(TRAINER_ID),
                isNull(),
                eq("ongoing"),
                eq("%Java\\%\\_Backend\\\\2026%"),
                pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    void UTCID02_listMyAssignedClasses_convertsNullStatusAndKeywordToNullFilters() {
        PageImpl<ClassAdminProjection> repositoryPage = new PageImpl<>(
                List.of(),
                PageRequest.of(0, 20),
                0);
        when(currentUserService.requireAuthenticatedUser()).thenReturn(trainer);
        when(classOfferingRepository.findTrainerAssignedClasses(
                eq(TRAINER_ID),
                isNull(),
                isNull(),
                isNull(),
                eq(PageRequest.of(0, 20))))
                .thenReturn(repositoryPage);

        PageResponse<ClassResponse> result = service.listMyAssignedClasses(
                null,
                null,
                null,
                0,
                20);

        assertThat(result.items()).isEmpty();
        assertThat(result.totalItems()).isZero();
    }

    @Test
    void UTCID03_listMyAssignedClasses_convertsBlankStatusAndKeywordToNullFilters() {
        PageImpl<ClassAdminProjection> repositoryPage = new PageImpl<>(
                List.of(),
                PageRequest.of(0, 10),
                0);
        when(currentUserService.requireAuthenticatedUser()).thenReturn(trainer);
        when(classOfferingRepository.findTrainerAssignedClasses(
                eq(TRAINER_ID),
                isNull(),
                isNull(),
                isNull(),
                eq(PageRequest.of(0, 10))))
                .thenReturn(repositoryPage);

        PageResponse<ClassResponse> result = service.listMyAssignedClasses(
                "   ",
                "   ",
                null,
                0,
                10);

        assertThat(result.items()).isEmpty();
        verify(classOfferingRepository).findTrainerAssignedClasses(
                TRAINER_ID,
                null,
                null,
                null,
                PageRequest.of(0, 10));
    }

    @Test
    void UTCID04_listMyAssignedClasses_acceptsUpcomingStatusIgnoringCase() {
        PageImpl<ClassAdminProjection> repositoryPage = new PageImpl<>(
                List.of(),
                PageRequest.of(0, 20),
                0);
        when(currentUserService.requireAuthenticatedUser()).thenReturn(trainer);
        when(classOfferingRepository.findTrainerAssignedClasses(
                TRAINER_ID,
                null,
                "upcoming",
                "%Spring Boot%",
                PageRequest.of(0, 20)))
                .thenReturn(repositoryPage);

        PageResponse<ClassResponse> result = service.listMyAssignedClasses(
                "uPcOmInG",
                "Spring Boot",
                null,
                0,
                20);

        assertThat(result.items()).isEmpty();
    }

    @Test
    void UTCID05_listMyAssignedClasses_rejectsStatusArchived() {
        when(currentUserService.requireAuthenticatedUser()).thenReturn(trainer);

        assertThatThrownBy(() -> service.listMyAssignedClasses(
                "archived",
                "Java Backend",
                null,
                0,
                20))
                .isInstanceOfSatisfying(BusinessException.class, error -> {
                    assertThat(error.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST);
                    assertThat(error.getMessage()).isEqualTo(
                            "Class status must be upcoming, ongoing, completed, or cancelled");
                });

        verify(classOfferingRepository, never()).findTrainerAssignedClasses(
                any(), any(), any(), any(), any());
    }

    @Test
    void UTCID06_listMyAssignedClasses_mapsNullCapacityValuesToZero() {
        stubProjection(null, null);
        PageImpl<ClassAdminProjection> repositoryPage = new PageImpl<>(
                List.of(projection),
                PageRequest.of(0, 20),
                1);
        when(currentUserService.requireAuthenticatedUser()).thenReturn(trainer);
        when(classOfferingRepository.findTrainerAssignedClasses(
                TRAINER_ID,
                null,
                "completed",
                null,
                PageRequest.of(0, 20)))
                .thenReturn(repositoryPage);

        PageResponse<ClassResponse> result = service.listMyAssignedClasses(
                "COMPLETED",
                null,
                null,
                0,
                20);

        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.maxStudents()).isZero();
            assertThat(item.activeEnrollmentCount()).isZero();
            assertThat(item.availableSeats()).isZero();
        });
    }

    // getMyAssignedClassDetail(): UTCID restarts from UTCID01.

    @Test
    void UTCID01_getMyAssignedClassDetail_returnsClassAssignedToCurrentTrainer() {
        stubProjection(30, 12L);
        when(currentUserService.requireAuthenticatedUser()).thenReturn(trainer);
        when(classOfferingRepository.findTrainerAssignedClassDetail(CLASS_ID, TRAINER_ID))
                .thenReturn(Optional.of(projection));

        ClassResponse result = service.getMyAssignedClassDetail(CLASS_ID);

        assertThat(result.id()).isEqualTo(CLASS_ID);
        assertThat(result.trainerId()).isEqualTo(TRAINER_ID);
        assertThat(result.maxStudents()).isEqualTo(30);
        assertThat(result.activeEnrollmentCount()).isEqualTo(12);
        assertThat(result.availableSeats()).isEqualTo(18);
        verify(classOfferingRepository).findTrainerAssignedClassDetail(CLASS_ID, TRAINER_ID);
    }

    @Test
    void UTCID02_getMyAssignedClassDetail_clampsAvailableSeatsToZeroWhenOverCapacity() {
        stubProjection(10, 15L);
        when(currentUserService.requireAuthenticatedUser()).thenReturn(trainer);
        when(classOfferingRepository.findTrainerAssignedClassDetail(CLASS_ID, TRAINER_ID))
                .thenReturn(Optional.of(projection));

        ClassResponse result = service.getMyAssignedClassDetail(CLASS_ID);

        assertThat(result.maxStudents()).isEqualTo(10);
        assertThat(result.activeEnrollmentCount()).isEqualTo(15);
        assertThat(result.availableSeats()).isZero();
    }

    @Test
    void UTCID03_getMyAssignedClassDetail_rejectsClassNotAssignedToTrainer() {
        when(currentUserService.requireAuthenticatedUser()).thenReturn(trainer);
        when(classOfferingRepository.findTrainerAssignedClassDetail(
                MISSING_CLASS_ID,
                TRAINER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMyAssignedClassDetail(MISSING_CLASS_ID))
                .isInstanceOfSatisfying(BusinessException.class, error -> {
                    assertThat(error.errorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
                    assertThat(error.getMessage()).isEqualTo("Assigned class was not found");
                });
    }

    private void stubProjection(Integer maxStudents, Long activeEnrollmentCount) {
        when(projection.getId()).thenReturn(CLASS_ID);
        when(projection.getCourseId()).thenReturn(COURSE_ID);
        when(projection.getCourseTitle()).thenReturn("Java Backend Professional");
        when(projection.getClassName()).thenReturn("Java Backend Class 01");
        when(projection.getTrainerId()).thenReturn(TRAINER_ID);
        when(projection.getTrainerName()).thenReturn("Nguyen Van Trainer");
        when(projection.getMeetingUrl()).thenReturn("https://meet.google.com/abc-defg-hij");
        when(projection.getScheduleDescription()).thenReturn(
                "[{\"dayOfWeek\":\"MONDAY\",\"slotNumber\":2}]");
        when(projection.getPrice()).thenReturn(new BigDecimal("500000"));
        when(projection.getStartDate()).thenReturn(CLASS_START_DATE);
        when(projection.getEndDate()).thenReturn(CLASS_END_DATE);
        when(projection.getMaxStudents()).thenReturn(maxStudents);
        when(projection.getActiveEnrollmentCount()).thenReturn(activeEnrollmentCount);
        when(projection.getStatus()).thenReturn("ongoing");
        when(projection.getCreatedAt()).thenReturn(CREATED_AT);
        when(projection.getUpdatedAt()).thenReturn(UPDATED_AT);
    }
}