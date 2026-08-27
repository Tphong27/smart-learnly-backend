package com.smartlearnly.backend.classroom.schedule.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartlearnly.backend.classroom.opening.dto.OpeningScheduleItemResponse;
import com.smartlearnly.backend.classroom.opening.dto.OpeningScheduleDetailResponse;
import com.smartlearnly.backend.classroom.repository.ClassOfferingRepository;
import com.smartlearnly.backend.classroom.opening.repository.OpeningScheduleProjection;
import com.smartlearnly.backend.common.api.PageResponse;
import com.smartlearnly.backend.classroom.opening.service.OpeningScheduleService;
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
class OpeningScheduleServiceTest {

        private static final UUID CLASS_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
        private static final UUID COURSE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
        private static final UUID TRAINER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
        private static final UUID MISSING_CLASS_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");
        private static final UUID CATEGORY_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

        private static final LocalDate START_FROM = LocalDate.of(2026, 8, 10);
        private static final LocalDate START_TO = LocalDate.of(2026, 8, 31);
        private static final LocalDate CLASS_START_DATE = LocalDate.of(2026, 8, 15);
        private static final LocalDate CLASS_END_DATE = LocalDate.of(2026, 9, 15);
        private static final BigDecimal MIN_PRICE = new BigDecimal("0");
        private static final BigDecimal MAX_PRICE = new BigDecimal("500000");

        @Mock
        private ClassOfferingRepository classOfferingRepository;

        @Mock
        private OpeningScheduleProjection projection;

        private OpeningScheduleService service;

        @BeforeEach
        void setUp() {
                service = new OpeningScheduleService(classOfferingRepository);
        }

        @Test
        void UTCID01_list_returnsFilteredPageAndCapsRequestedSize500To100() {
                stubProjection(30, 12L);
                PageImpl<OpeningScheduleProjection> repositoryPage = new PageImpl<>(
                                List.of(projection),
                                PageRequest.of(1, 100),
                                101);

                when(classOfferingRepository.findOpeningSchedules(
                                eq("%Java\\%\\_Backend\\\\2026%"),
                                eq(COURSE_ID),
                                eq(START_FROM),
                                eq(START_TO),
                                eq(MIN_PRICE),
                                eq(MAX_PRICE),
                                any(Pageable.class)))
                                .thenReturn(repositoryPage);

                PageResponse<OpeningScheduleItemResponse> result = service.list(
                                "  Java%_Backend\\2026  ",
                                COURSE_ID,
                                START_FROM,
                                START_TO,
                                MIN_PRICE,
                                MAX_PRICE,
                                1,
                                500);

                assertThat(result.page()).isEqualTo(1);
                assertThat(result.size()).isEqualTo(100);
                assertThat(result.totalItems()).isEqualTo(101);
                assertThat(result.totalPages()).isEqualTo(2);
                assertThat(result.items()).singleElement().satisfies(item -> {
                        assertThat(item.classId()).isEqualTo(CLASS_ID);
                        assertThat(item.courseId()).isEqualTo(COURSE_ID);
                        assertThat(item.courseTitle()).isEqualTo("Java Backend Professional");
                        assertThat(item.courseSlug()).isEqualTo("java-backend-professional");
                        assertThat(item.courseThumbnailUrl()).isEqualTo("/images/java-backend.png");
                        assertThat(item.className()).isEqualTo("Java Backend Class 01");
                        assertThat(item.trainerId()).isEqualTo(TRAINER_ID);
                        assertThat(item.trainerName()).isEqualTo("Nguyen Van Trainer");
                        assertThat(item.startDate()).isEqualTo(CLASS_START_DATE);
                        assertThat(item.endDate()).isEqualTo(CLASS_END_DATE);
                        assertThat(item.scheduleDescription()).isEqualTo(
                                        "[{\"dayOfWeek\":\"MONDAY\",\"slotNumber\":2}]");
                        assertThat(item.price()).isEqualByComparingTo("500000");
                        assertThat(item.maxStudents()).isEqualTo(30);
                        assertThat(item.activeEnrollmentCount()).isEqualTo(12);
                        assertThat(item.availableSlots()).isEqualTo(18);
                        assertThat(item.status()).isEqualTo("upcoming");
                });

                ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
                verify(classOfferingRepository).findOpeningSchedules(
                                eq("%Java\\%\\_Backend\\\\2026%"),
                                eq(COURSE_ID),
                                eq(START_FROM),
                                eq(START_TO),
                                eq(MIN_PRICE),
                                eq(MAX_PRICE),
                                pageableCaptor.capture());
                assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(1);
                assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(100);
        }

        @Test
        void UTCID02_list_convertsNullKeywordAndNullCapacityValuesToZero() {
                stubProjection(null, null);
                PageImpl<OpeningScheduleProjection> repositoryPage = new PageImpl<>(
                                List.of(projection),
                                PageRequest.of(0, 20),
                                1);

                when(classOfferingRepository.findOpeningSchedules(
                                isNull(),
                                isNull(),
                                isNull(),
                                isNull(),
                                isNull(),
                                isNull(),
                                eq(PageRequest.of(0, 20))))
                                .thenReturn(repositoryPage);

                PageResponse<OpeningScheduleItemResponse> result = service.list(
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                0,
                                20);

                assertThat(result.items()).singleElement().satisfies(item -> {
                        assertThat(item.maxStudents()).isZero();
                        assertThat(item.activeEnrollmentCount()).isZero();
                        assertThat(item.availableSlots()).isZero();
                });
        }

        @Test
        void UTCID03_list_convertsBlankKeywordToNullSearchPattern() {
                PageImpl<OpeningScheduleProjection> repositoryPage = new PageImpl<>(
                                List.of(),
                                PageRequest.of(0, 10),
                                0);

                when(classOfferingRepository.findOpeningSchedules(
                                isNull(),
                                eq(COURSE_ID),
                                isNull(),
                                isNull(),
                                eq(MIN_PRICE),
                                eq(MAX_PRICE),
                                eq(PageRequest.of(0, 10))))
                                .thenReturn(repositoryPage);

                PageResponse<OpeningScheduleItemResponse> result = service.list(
                                "   ",
                                COURSE_ID,
                                null,
                                null,
                                MIN_PRICE,
                                MAX_PRICE,
                                0,
                                10);

                assertThat(result.items()).isEmpty();
                assertThat(result.totalItems()).isZero();
        }

        @Test
        void UTCID04_list_rejectsPageMinusOne() {
                assertInvalidRequest(
                                () -> service.list(null, null, null, null, null, null, -1, 20),
                                "Page index cannot be negative");

                verify(classOfferingRepository, never()).findOpeningSchedules(
                                any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        void UTCID05_list_rejectsPageSizeZero() {
                assertInvalidRequest(
                                () -> service.list(null, null, null, null, null, null, 0, 0),
                                "Page size must be greater than zero");

                verify(classOfferingRepository, never()).findOpeningSchedules(
                                any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        void UTCID06_list_rejectsStartFrom20260831AfterStartTo20260810() {
                assertInvalidRequest(
                                () -> service.list(
                                                null,
                                                null,
                                                LocalDate.of(2026, 8, 31),
                                                LocalDate.of(2026, 8, 10),
                                                null,
                                                null,
                                                0,
                                                20),
                                "Start-from date cannot be after start-to date");
        }

        @Test
        void UTCID07_list_rejectsMinimumPriceMinusOne() {
                assertInvalidRequest(
                                () -> service.list(
                                                null,
                                                null,
                                                null,
                                                null,
                                                new BigDecimal("-1"),
                                                MAX_PRICE,
                                                0,
                                                20),
                                "Minimum price cannot be negative");
        }

        @Test
        void UTCID08_list_rejectsMaximumPriceMinusOne() {
                assertInvalidRequest(
                                () -> service.list(
                                                null,
                                                null,
                                                null,
                                                null,
                                                MIN_PRICE,
                                                new BigDecimal("-1"),
                                                0,
                                                20),
                                "Maximum price cannot be negative");
        }

        @Test
        void UTCID09_list_rejectsMinimumPrice500001AboveMaximumPrice500000() {
                assertInvalidRequest(
                                () -> service.list(
                                                null,
                                                null,
                                                null,
                                                null,
                                                new BigDecimal("500001"),
                                                new BigDecimal("500000"),
                                                0,
                                                20),
                                "Minimum price cannot exceed maximum price");
        }

        @Test
        void UTCID10_list_clampsAvailableSlotsToZeroWhenActiveCount35ExceedsCapacity30() {
                stubProjection(30, 35L);
                PageImpl<OpeningScheduleProjection> repositoryPage = new PageImpl<>(
                                List.of(projection),
                                PageRequest.of(0, 20),
                                1);

                when(classOfferingRepository.findOpeningSchedules(
                                isNull(),
                                isNull(),
                                isNull(),
                                isNull(),
                                isNull(),
                                isNull(),
                                eq(PageRequest.of(0, 20))))
                                .thenReturn(repositoryPage);

                PageResponse<OpeningScheduleItemResponse> result = service.list(
                                null, null, null, null, null, null, 0, 20);

                assertThat(result.items()).singleElement().satisfies(item -> {
                        assertThat(item.maxStudents()).isEqualTo(30);
                        assertThat(item.activeEnrollmentCount()).isEqualTo(35);
                        assertThat(item.availableSlots()).isZero();
                });
        }

        @Test
        void UTCID01_getDetail_returnsOpeningClassForClassId11111111() {
                stubProjection(30, 12L);
                when(classOfferingRepository.findOpeningScheduleDetail(CLASS_ID))
                                .thenReturn(Optional.of(projection));

                OpeningScheduleDetailResponse result = service.getDetail(CLASS_ID);

                assertThat(result.classId()).isEqualTo(CLASS_ID);
                assertThat(result.courseId()).isEqualTo(COURSE_ID);
                assertThat(result.courseTitle())
                                .isEqualTo("Java Backend Professional");
                assertThat(result.courseShortDescription())
                                .isEqualTo("Build reliable Java backend applications.");
                assertThat(result.courseDescription())
                                .isEqualTo("Learn Java, Spring Boot, REST API and PostgreSQL.");
                assertThat(result.courseLanguage()).isEqualTo("Vietnamese");
                assertThat(result.courseLevel()).isEqualTo("Intermediate");
                assertThat(result.courseCategoryId()).isNotNull();
                assertThat(result.courseCategoryName()).isEqualTo("Programming");
                assertThat(result.courseCategorySlug()).isEqualTo("programming");
                assertThat(result.maxStudents()).isEqualTo(30);
                assertThat(result.activeEnrollmentCount()).isEqualTo(12);
                assertThat(result.availableSlots()).isEqualTo(18);
                verify(classOfferingRepository).findOpeningScheduleDetail(CLASS_ID);
        }

        @Test
        void UTCID02_getDetail_rejectsNullClassId() {
                assertInvalidRequest(
                                () -> service.getDetail(null),
                                "Class ID is required");

                verify(classOfferingRepository, never()).findOpeningScheduleDetail(any());
        }

        @Test
        void UTCID03_getDetail_rejectsMissingClassId99999999() {
                when(classOfferingRepository.findOpeningScheduleDetail(MISSING_CLASS_ID))
                                .thenReturn(Optional.empty());

                assertThatThrownBy(() -> service.getDetail(MISSING_CLASS_ID))
                                .isInstanceOfSatisfying(BusinessException.class, error -> {
                                        assertThat(error.errorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
                                        assertThat(error.getMessage()).isEqualTo("Opening class was not found");
                                });
        }

        private void stubProjection(Integer maxStudents, Long activeEnrollmentCount) {
                when(projection.getClassId()).thenReturn(CLASS_ID);
                when(projection.getCourseId()).thenReturn(COURSE_ID);
                when(projection.getCourseTitle()).thenReturn("Java Backend Professional");
                when(projection.getCourseSlug()).thenReturn("java-backend-professional");
                when(projection.getCourseThumbnailUrl()).thenReturn("/images/java-backend.png");
                when(projection.getCourseThumbnailUrl())
                                .thenReturn("/images/java-backend.png");

                when(projection.getCourseShortDescription())
                                .thenReturn("Build reliable Java backend applications.");

                when(projection.getCourseDescription())
                                .thenReturn(
                                                "Learn Java, Spring Boot, REST API and PostgreSQL.");

                when(projection.getCourseLanguage())
                                .thenReturn("Vietnamese");

                when(projection.getCourseLevel())
                                .thenReturn("Intermediate");

                when(projection.getCourseCategoryId())
                                .thenReturn(CATEGORY_ID);

                when(projection.getCourseCategoryName())
                                .thenReturn("Programming");

                when(projection.getCourseCategorySlug())
                                .thenReturn("programming");

                when(projection.getClassName());
                when(projection.getClassName()).thenReturn("Java Backend Class 01");
                when(projection.getTrainerId()).thenReturn(TRAINER_ID);
                when(projection.getTrainerName()).thenReturn("Nguyen Van Trainer");
                when(projection.getStartDate()).thenReturn(CLASS_START_DATE);
                when(projection.getEndDate()).thenReturn(CLASS_END_DATE);
                when(projection.getScheduleDescription()).thenReturn(
                                "[{\"dayOfWeek\":\"MONDAY\",\"slotNumber\":2}]");
                when(projection.getPrice()).thenReturn(new BigDecimal("500000"));
                when(projection.getMaxStudents()).thenReturn(maxStudents);
                when(projection.getActiveEnrollmentCount()).thenReturn(activeEnrollmentCount);
                when(projection.getStatus()).thenReturn("upcoming");
        }

        private void assertInvalidRequest(Runnable invocation, String expectedMessage) {
                assertThatThrownBy(invocation::run)
                                .isInstanceOfSatisfying(BusinessException.class, error -> {
                                        assertThat(error.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST);
                                        assertThat(error.getMessage()).isEqualTo(expectedMessage);
                                });
        }
}