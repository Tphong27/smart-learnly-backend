package com.smartlearnly.backend.assignment.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SubmissionStatusConverterTest {

    private final SubmissionStatusConverter converter = new SubmissionStatusConverter();

    @Test
    void convertToDatabaseColumn_mapsLegacyPendingToDoing() {
        assertThat(converter.convertToDatabaseColumn(SubmissionStatus.PENDING)).isEqualTo("doing");
    }

    @Test
    void convertToDatabaseColumn_mapsLegacyLateToExpired() {
        assertThat(converter.convertToDatabaseColumn(SubmissionStatus.LATE)).isEqualTo("expired");
    }

    @Test
    void convertToDatabaseColumn_keepsCanonicalStatuses() {
        assertThat(converter.convertToDatabaseColumn(SubmissionStatus.SUBMITTED)).isEqualTo("submitted");
        assertThat(converter.convertToDatabaseColumn(SubmissionStatus.GRADED)).isEqualTo("graded");
        assertThat(converter.convertToDatabaseColumn(SubmissionStatus.EXPIRED)).isEqualTo("expired");
    }

    @Test
    void convertToEntityAttribute_readsLegacyStatuses() {
        assertThat(converter.convertToEntityAttribute("pending")).isEqualTo(SubmissionStatus.DOING);
        assertThat(converter.convertToEntityAttribute("late")).isEqualTo(SubmissionStatus.EXPIRED);
    }
}
