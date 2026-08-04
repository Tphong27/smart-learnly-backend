package com.smartlearnly.backend.classroom.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ClassLifecycleTest {

    private static final LocalDate REFERENCE_DATE = LocalDate.of(2026, 8, 4);

    // Cả hai overload thuộc cùng chức năng resolveStatus().
    // UTCID được đánh liên tục từ UTCID01 đến UTCID12.

    @Test
    void UTCID01_resolveStatusWithoutToday_returnsUpcomingForFutureClass() {
        ClassStatus result = ClassLifecycle.resolveStatus(
                LocalDate.of(2999, 1, 1),
                LocalDate.of(2999, 12, 31),
                null);

        assertThat(result).isEqualTo(ClassStatus.UPCOMING);
    }

    @Test
    void UTCID02_resolveStatusWithoutToday_returnsCompletedForPastClass() {
        ClassStatus result = ClassLifecycle.resolveStatus(
                LocalDate.of(1900, 1, 1),
                LocalDate.of(1900, 12, 31),
                ClassStatus.ONGOING);

        assertThat(result).isEqualTo(ClassStatus.COMPLETED);
    }

    @Test
    void UTCID03_resolveStatusWithoutToday_preservesCancelledStatus() {
        ClassStatus result = ClassLifecycle.resolveStatus(
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                ClassStatus.CANCELLED);

        assertThat(result).isEqualTo(ClassStatus.CANCELLED);
    }

    @Test
    void UTCID04_resolveStatusWithToday_rejectsNullStartDate() {
        assertThatThrownBy(() -> ClassLifecycle.resolveStatus(
                null,
                LocalDate.of(2026, 8, 31),
                ClassStatus.UPCOMING,
                REFERENCE_DATE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Start date is required to resolve class status");
    }

    @Test
    void UTCID05_resolveStatusWithToday_rejectsNullEndDate() {
        assertThatThrownBy(() -> ClassLifecycle.resolveStatus(
                LocalDate.of(2026, 8, 1),
                null,
                ClassStatus.UPCOMING,
                REFERENCE_DATE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "End date is required to resolve class status");
    }

    @Test
    void UTCID06_resolveStatusWithToday_rejectsNullCurrentDate() {
        assertThatThrownBy(() -> ClassLifecycle.resolveStatus(
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                ClassStatus.UPCOMING,
                null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Current date is required to resolve class status");
    }

    @Test
    void UTCID07_resolveStatusWithToday_rejectsEndDateBeforeStartDate() {
        assertThatThrownBy(() -> ClassLifecycle.resolveStatus(
                LocalDate.of(2026, 8, 31),
                LocalDate.of(2026, 8, 1),
                ClassStatus.UPCOMING,
                REFERENCE_DATE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "End date must not be before start date");
    }

    @Test
    void UTCID08_resolveStatusWithToday_preservesCancelledStatus() {
        ClassStatus result = ClassLifecycle.resolveStatus(
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                ClassStatus.CANCELLED,
                REFERENCE_DATE);

        assertThat(result).isEqualTo(ClassStatus.CANCELLED);
    }

    @Test
    void UTCID09_resolveStatusWithToday_returnsCompletedAfterEndDate() {
        ClassStatus result = ClassLifecycle.resolveStatus(
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 8, 3),
                ClassStatus.ONGOING,
                REFERENCE_DATE);

        assertThat(result).isEqualTo(ClassStatus.COMPLETED);
    }

    @Test
    void UTCID10_resolveStatusWithToday_returnsUpcomingBeforeStartDate() {
        ClassStatus result = ClassLifecycle.resolveStatus(
                LocalDate.of(2026, 8, 5),
                LocalDate.of(2026, 8, 31),
                ClassStatus.COMPLETED,
                REFERENCE_DATE);

        assertThat(result).isEqualTo(ClassStatus.UPCOMING);
    }

    @Test
    void UTCID11_resolveStatusWithToday_returnsOngoingOnStartDate() {
        ClassStatus result = ClassLifecycle.resolveStatus(
                REFERENCE_DATE,
                LocalDate.of(2026, 8, 31),
                ClassStatus.UPCOMING,
                REFERENCE_DATE);

        assertThat(result).isEqualTo(ClassStatus.ONGOING);
    }

    @Test
    void UTCID12_resolveStatusWithToday_returnsOngoingOnEndDate() {
        ClassStatus result = ClassLifecycle.resolveStatus(
                LocalDate.of(2026, 8, 1),
                REFERENCE_DATE,
                ClassStatus.ONGOING,
                REFERENCE_DATE);

        assertThat(result).isEqualTo(ClassStatus.ONGOING);
    }
}