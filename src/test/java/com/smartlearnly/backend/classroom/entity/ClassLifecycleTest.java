package com.smartlearnly.backend.classroom.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ClassLifecycleTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 7, 26);

    @Test
    void shouldResolveUpcomingBeforeStartDate() {
        ClassStatus status = ClassLifecycle.resolveStatus(
                TODAY.plusDays(1),
                TODAY.plusMonths(1),
                ClassStatus.UPCOMING,
                TODAY);

        assertThat(status).isEqualTo(ClassStatus.UPCOMING);
    }

    @Test
    void shouldResolveOngoingOnStartDate() {
        ClassStatus status = ClassLifecycle.resolveStatus(
                TODAY,
                TODAY.plusMonths(1),
                ClassStatus.UPCOMING,
                TODAY);

        assertThat(status).isEqualTo(ClassStatus.ONGOING);
    }

    @Test
    void shouldResolveOngoingOnEndDate() {
        ClassStatus status = ClassLifecycle.resolveStatus(
                TODAY.minusMonths(1),
                TODAY,
                ClassStatus.ONGOING,
                TODAY);

        assertThat(status).isEqualTo(ClassStatus.ONGOING);
    }

    @Test
    void shouldResolveCompletedAfterEndDate() {
        ClassStatus status = ClassLifecycle.resolveStatus(
                TODAY.minusMonths(1),
                TODAY.minusDays(1),
                ClassStatus.ONGOING,
                TODAY);

        assertThat(status).isEqualTo(ClassStatus.COMPLETED);
    }

    @Test
    void shouldPreserveCancelledStatus() {
        ClassStatus status = ClassLifecycle.resolveStatus(
                TODAY.minusMonths(1),
                TODAY.plusMonths(1),
                ClassStatus.CANCELLED,
                TODAY);

        assertThat(status).isEqualTo(ClassStatus.CANCELLED);
    }
}