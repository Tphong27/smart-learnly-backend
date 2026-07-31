package com.smartlearnly.backend.classroom.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class KhiemClassStatusConverterReportTest {

    private final ClassStatusConverter converter = new ClassStatusConverter();

    @Test
    void UTCID_KHIEM_BE_486_convertToDatabaseColumn_convertsEnumToLowercase() {
        assertThat(converter.convertToDatabaseColumn(ClassStatus.UPCOMING)).isEqualTo("upcoming");
        assertThat(converter.convertToDatabaseColumn(ClassStatus.CANCELLED)).isEqualTo("cancelled");
    }

    @Test
    void UTCID_KHIEM_BE_487_convertToDatabaseColumn_preservesNull() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    void UTCID_KHIEM_BE_488_convertToEntityAttribute_acceptsMixedCase() {
        assertThat(converter.convertToEntityAttribute("OnGoInG")).isEqualTo(ClassStatus.ONGOING);
        assertThat(converter.convertToEntityAttribute("completed")).isEqualTo(ClassStatus.COMPLETED);
    }

    @Test
    void UTCID_KHIEM_BE_489_convertToEntityAttribute_handlesNullAndRejectsUnknownValue() {
        assertThat(converter.convertToEntityAttribute(null)).isNull();
        assertThatThrownBy(() -> converter.convertToEntityAttribute("archived"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
