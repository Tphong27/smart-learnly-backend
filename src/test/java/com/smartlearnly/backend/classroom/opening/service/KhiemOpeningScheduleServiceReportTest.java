package com.smartlearnly.backend.classroom.opening.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.classroom.opening.dto.OpeningScheduleItemResponse;
import com.smartlearnly.backend.classroom.opening.repository.OpeningScheduleProjection;
import com.smartlearnly.backend.classroom.repository.ClassOfferingRepository;
import com.smartlearnly.backend.common.api.PageResponse;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import java.math.BigDecimal;
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
class KhiemOpeningScheduleServiceReportTest {

    @Mock
    private ClassOfferingRepository classOfferingRepository;

    private OpeningScheduleService service;

    @BeforeEach
    void setUp() {
        service = new OpeningScheduleService(classOfferingRepository);
    }

    @Test
    void UTCID_KHIEM_BE_514_list_escapesKeywordCapsSizeAndMapsAvailability() {
        OpeningScheduleProjection projection = projection(30, 35L);
        PageImpl<OpeningScheduleProjection> page =
                new PageImpl<>(List.of(projection), PageRequest.of(0, 100), 1);
        when(classOfferingRepository.findOpeningSchedules(
                org.mockito.ArgumentMatchers.eq("%Java\\%\\_101%"),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq(BigDecimal.ZERO),
                org.mockito.ArgumentMatchers.eq(new BigDecimal("500000")),
                org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(page);

        PageResponse<OpeningScheduleItemResponse> result = service.list(
                " Java%_101 ",
                null,
                null,
                null,
                BigDecimal.ZERO,
                new BigDecimal("500000"),
                0,
                500);

        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.classId()).isEqualTo(projection.getClassId());
            assertThat(item.availableSlots()).isZero();
            assertThat(item.activeEnrollmentCount()).isEqualTo(35);
        });
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(classOfferingRepository).findOpeningSchedules(
                org.mockito.ArgumentMatchers.eq("%Java\\%\\_101%"),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq(BigDecimal.ZERO),
                org.mockito.ArgumentMatchers.eq(new BigDecimal("500000")),
                pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    void UTCID_KHIEM_BE_515_list_rejectsInvalidDateAndPriceRanges() {
        LocalDate start = LocalDate.of(2026, 8, 2);
        LocalDate end = LocalDate.of(2026, 8, 1);

        assertThatThrownBy(() -> service.list(
                null, null, start, end, null, null, 0, 20))
                .isInstanceOfSatisfying(BusinessException.class, error ->
                        assertThat(error.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
        assertThatThrownBy(() -> service.list(
                null, null, null, null,
                new BigDecimal("10"), BigDecimal.ZERO, 0, 20))
                .isInstanceOfSatisfying(BusinessException.class, error ->
                        assertThat(error.getMessage())
                                .contains("Minimum price cannot exceed maximum price"));
    }

    @Test
    void UTCID_KHIEM_BE_516_getDetail_rejectsNullClassId() {
        assertThatThrownBy(() -> service.getDetail(null))
                .isInstanceOfSatisfying(BusinessException.class, error -> {
                    assertThat(error.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST);
                    assertThat(error.getMessage()).isEqualTo("Class ID is required");
                });
    }

    @Test
    void UTCID_KHIEM_BE_517_getDetail_returnsOpeningClass() {
        OpeningScheduleProjection projection = projection(30, null);
        when(classOfferingRepository.findOpeningScheduleDetail(projection.getClassId()))
                .thenReturn(Optional.of(projection));

        OpeningScheduleItemResponse result = service.getDetail(projection.getClassId());

        assertThat(result.classId()).isEqualTo(projection.getClassId());
        assertThat(result.activeEnrollmentCount()).isZero();
        assertThat(result.availableSlots()).isEqualTo(30);
    }

    @Test
    void UTCID_KHIEM_BE_518_getDetail_rejectsMissingOpeningClass() {
        UUID classId = UUID.randomUUID();
        when(classOfferingRepository.findOpeningScheduleDetail(classId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDetail(classId))
                .isInstanceOfSatisfying(BusinessException.class, error ->
                        assertThat(error.errorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private OpeningScheduleProjection projection(int maxStudents, Long activeCount) {
        OpeningScheduleProjection projection = mock(OpeningScheduleProjection.class);
        when(projection.getClassId()).thenReturn(UUID.randomUUID());
        when(projection.getCourseId()).thenReturn(UUID.randomUUID());
        when(projection.getCourseTitle()).thenReturn("Java Backend");
        when(projection.getCourseSlug()).thenReturn("java-backend");
        when(projection.getCourseThumbnailUrl()).thenReturn("/java.png");
        when(projection.getClassName()).thenReturn("Java Class");
        when(projection.getTrainerId()).thenReturn(UUID.randomUUID());
        when(projection.getTrainerName()).thenReturn("Trainer");
        when(projection.getStartDate()).thenReturn(LocalDate.of(2026, 8, 1));
        when(projection.getEndDate()).thenReturn(LocalDate.of(2026, 9, 1));
        when(projection.getScheduleDescription()).thenReturn("Monday");
        when(projection.getPrice()).thenReturn(BigDecimal.ZERO);
        when(projection.getMaxStudents()).thenReturn(maxStudents);
        when(projection.getActiveEnrollmentCount()).thenReturn(activeCount);
        when(projection.getStatus()).thenReturn("upcoming");
        return projection;
    }
}
