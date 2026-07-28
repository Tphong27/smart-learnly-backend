package com.smartlearnly.backend.classroom.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class KhiemClassTrainerServiceReportTest {

    @Mock
    private ClassOfferingRepository classOfferingRepository;

    @Mock
    private CurrentUserService currentUserService;

    private ClassTrainerService service;

    @BeforeEach
    void setUp() {
        service = new ClassTrainerService(classOfferingRepository, currentUserService);
    }

    @Test
    void UTCID_KHIEM_BE_507_listMyAssignedClasses_normalizesFiltersAndCapsPageSize() {
        UserAccount trainer = trainer();
        ClassAdminProjection projection = projection(30, 5L, trainer.getId());
        when(currentUserService.requireAuthenticatedUser()).thenReturn(trainer);
        when(classOfferingRepository.findTrainerAssignedClasses(
                org.mockito.ArgumentMatchers.eq(trainer.getId()),
                org.mockito.ArgumentMatchers.eq("ongoing"),
                org.mockito.ArgumentMatchers.eq("%Java\\%\\_101%"),
                org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(new PageImpl<>(java.util.List.of(projection)));

        PageResponse<ClassResponse> result = service.listMyAssignedClasses(
                " ONGOING ",
                " Java%_101 ",
                0,
                500);

        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.maxStudents()).isEqualTo(30);
            assertThat(item.activeEnrollmentCount()).isEqualTo(5);
            assertThat(item.availableSeats()).isEqualTo(25);
        });
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(classOfferingRepository).findTrainerAssignedClasses(
                org.mockito.ArgumentMatchers.eq(trainer.getId()),
                org.mockito.ArgumentMatchers.eq("ongoing"),
                org.mockito.ArgumentMatchers.eq("%Java\\%\\_101%"),
                pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    void UTCID_KHIEM_BE_508_listMyAssignedClasses_rejectsInvalidStatus() {
        when(currentUserService.requireAuthenticatedUser()).thenReturn(trainer());

        assertThatThrownBy(() -> service.listMyAssignedClasses(
                "archived", null, 0, 20))
                .isInstanceOfSatisfying(BusinessException.class, error ->
                        assertThat(error.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
    }

    @Test
    void UTCID_KHIEM_BE_509_getMyAssignedClassDetail_returnsOnlyAssignedProjection() {
        UserAccount trainer = trainer();
        ClassAdminProjection projection = projection(10, 15L, trainer.getId());
        when(currentUserService.requireAuthenticatedUser()).thenReturn(trainer);
        when(classOfferingRepository.findTrainerAssignedClassDetail(
                projection.getId(), trainer.getId()))
                .thenReturn(Optional.of(projection));

        ClassResponse result = service.getMyAssignedClassDetail(projection.getId());

        assertThat(result.id()).isEqualTo(projection.getId());
        assertThat(result.availableSeats()).isZero();
        assertThat(result.trainerId()).isEqualTo(trainer.getId());
    }

    @Test
    void UTCID_KHIEM_BE_510_getMyAssignedClassDetail_hidesUnassignedClass() {
        UserAccount trainer = trainer();
        UUID classId = UUID.randomUUID();
        when(currentUserService.requireAuthenticatedUser()).thenReturn(trainer);
        when(classOfferingRepository.findTrainerAssignedClassDetail(
                classId, trainer.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMyAssignedClassDetail(classId))
                .isInstanceOfSatisfying(BusinessException.class, error -> {
                    assertThat(error.errorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
                    assertThat(error.getMessage()).contains("Assigned class was not found");
                });
    }

    private UserAccount trainer() {
        UserAccount trainer = new UserAccount();
        trainer.setId(UUID.randomUUID());
        trainer.setRole("TRAINER");
        trainer.setStatus("active");
        trainer.setEmail("trainer@example.com");
        trainer.setFullName("Trainer");
        return trainer;
    }

    private ClassAdminProjection projection(
            int maxStudents,
            Long activeCount,
            UUID trainerId) {
        ClassAdminProjection projection = mock(ClassAdminProjection.class);
        when(projection.getId()).thenReturn(UUID.randomUUID());
        when(projection.getCourseId()).thenReturn(UUID.randomUUID());
        when(projection.getCourseTitle()).thenReturn("Java Backend");
        when(projection.getClassName()).thenReturn("Java Class");
        when(projection.getTrainerId()).thenReturn(trainerId);
        when(projection.getTrainerName()).thenReturn("Trainer");
        when(projection.getMeetingUrl()).thenReturn("https://meet.google.com/abc-defg-hij");
        when(projection.getScheduleDescription()).thenReturn("Monday");
        when(projection.getPrice()).thenReturn(BigDecimal.ZERO);
        when(projection.getStartDate()).thenReturn(LocalDate.of(2026, 8, 1));
        when(projection.getEndDate()).thenReturn(LocalDate.of(2026, 9, 1));
        when(projection.getMaxStudents()).thenReturn(maxStudents);
        when(projection.getActiveEnrollmentCount()).thenReturn(activeCount);
        when(projection.getStatus()).thenReturn("upcoming");
        when(projection.getCreatedAt()).thenReturn(Instant.parse("2026-07-01T00:00:00Z"));
        when(projection.getUpdatedAt()).thenReturn(Instant.parse("2026-07-02T00:00:00Z"));
        return projection;
    }
}
