package com.smartlearnly.backend.classroom.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class KhiemClassLifecycleReportTest {

    private static final LocalDate REFERENCE_DATE = LocalDate.of(2026, 7, 28);

    @Test
    void UTCID_KHIEM_BE_481_resolveStatusWithToday_preservesCancelledStatus() {
        ClassStatus result = ClassLifecycle.resolveStatus(
                REFERENCE_DATE.minusDays(1),
                REFERENCE_DATE.plusDays(1),
                ClassStatus.CANCELLED,
                REFERENCE_DATE);

        assertThat(result).isEqualTo(ClassStatus.CANCELLED);
    }

    @Test
    void UTCID_KHIEM_BE_482_resolveStatusWithToday_resolvesDateBoundaries() {
        assertThat(ClassLifecycle.resolveStatus(
                REFERENCE_DATE.plusDays(1),
                REFERENCE_DATE.plusDays(2),
                null,
                REFERENCE_DATE)).isEqualTo(ClassStatus.UPCOMING);
        assertThat(ClassLifecycle.resolveStatus(
                REFERENCE_DATE,
                REFERENCE_DATE,
                null,
                REFERENCE_DATE)).isEqualTo(ClassStatus.UPCOMING);
        assertThat(ClassLifecycle.resolveStatus(
                REFERENCE_DATE.minusDays(1),
                REFERENCE_DATE.plusDays(1),
                null,
                REFERENCE_DATE)).isEqualTo(ClassStatus.ONGOING);
        assertThat(ClassLifecycle.resolveStatus(
                REFERENCE_DATE.minusDays(2),
                REFERENCE_DATE.minusDays(1),
                null,
                REFERENCE_DATE)).isEqualTo(ClassStatus.COMPLETED);
    }

    @Test
    void UTCID_KHIEM_BE_483_resolveStatusWithToday_rejectsInvalidDates() {
        assertThatThrownBy(() -> ClassLifecycle.resolveStatus(
                null,
                REFERENCE_DATE,
                null,
                REFERENCE_DATE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Start date is required to resolve class status");

        assertThatThrownBy(() -> ClassLifecycle.resolveStatus(
                REFERENCE_DATE,
                REFERENCE_DATE.minusDays(1),
                null,
                REFERENCE_DATE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("End date must not be before start date");
    }

    @Test
    void UTCID_KHIEM_BE_484_resolveStatus_usesBusinessToday() {
        LocalDate businessToday = ClassLifecycle.today();

        assertThat(ClassLifecycle.resolveStatus(
                businessToday.plusDays(1),
                businessToday.plusDays(2),
                null)).isEqualTo(ClassStatus.UPCOMING);
        assertThat(ClassLifecycle.resolveStatus(
                businessToday.minusDays(2),
                businessToday.minusDays(1),
                null)).isEqualTo(ClassStatus.COMPLETED);
    }

    @Test
    void UTCID_KHIEM_BE_485_today_returnsDateInBusinessZone() {
        LocalDate before = LocalDate.now(ClassLifecycle.BUSINESS_ZONE);
        LocalDate result = ClassLifecycle.today();
        LocalDate after = LocalDate.now(ClassLifecycle.BUSINESS_ZONE);

        assertThat(result).isBetween(before, after);
    }
}
