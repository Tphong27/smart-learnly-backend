package com.smartlearnly.backend.curriculum.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartlearnly.backend.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

class CurriculumRequestNormalizerTest {

    @Test
    void normalizeRequired_validString_returnsTrimmed() {
        String result = CurriculumRequestNormalizer.normalizeRequired("  hello  ", "message");
        assertThat(result).isEqualTo("hello");
    }

    @Test
    void normalizeRequired_null_throwsException() {
        assertThatThrownBy(() -> CurriculumRequestNormalizer.normalizeRequired(null, "custom message"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("custom message");
    }

    @Test
    void normalizeRequired_blank_throwsException() {
        assertThatThrownBy(() -> CurriculumRequestNormalizer.normalizeRequired("   ", "blank message"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("blank message");
    }

    @Test
    void normalizeNullable_null_returnsNull() {
        String result = CurriculumRequestNormalizer.normalizeNullable(null);
        assertThat(result).isNull();
    }

    @Test
    void normalizeNullable_blank_returnsNull() {
        String result = CurriculumRequestNormalizer.normalizeNullable("   ");
        assertThat(result).isNull();
    }

    @Test
    void normalizeNullable_validString_returnsTrimmed() {
        String result = CurriculumRequestNormalizer.normalizeNullable("  hello  ");
        assertThat(result).isEqualTo("hello");
    }

    @Test
    void fileNameFromUrl_withPath_returnsFilename() {
        String result = CurriculumRequestNormalizer.fileNameFromUrl("https://example.com/path/file.pdf");
        assertThat(result).isEqualTo("file.pdf");
    }

    @Test
    void fileNameFromUrl_withQueryString_stripsQuery() {
        String result = CurriculumRequestNormalizer.fileNameFromUrl("https://example.com/file.pdf?token=abc");
        assertThat(result).isEqualTo("file.pdf");
    }

    @Test
    void fileNameFromUrl_withFragment_stripsFragment() {
        String result = CurriculumRequestNormalizer.fileNameFromUrl("https://example.com/file.pdf#section");
        assertThat(result).isEqualTo("file.pdf");
    }

    @Test
    void fileNameFromUrl_withQueryAndFragment_stripsBoth() {
        String result = CurriculumRequestNormalizer.fileNameFromUrl("https://example.com/path/file.pdf?token=abc#section");
        assertThat(result).isEqualTo("file.pdf");
    }

    @Test
    void fileNameFromUrl_noPath_returnsFull() {
        String result = CurriculumRequestNormalizer.fileNameFromUrl("filename-only");
        assertThat(result).isEqualTo("filename-only");
    }

    @Test
    void fileNameFromUrl_null_returnsNull() {
        String result = CurriculumRequestNormalizer.fileNameFromUrl(null);
        assertThat(result).isNull();
    }

    @Test
    void validateResourceNameLength_validLength_doesNotThrow() {
        CurriculumRequestNormalizer.validateResourceNameLength("valid-name.pdf");
    }

    @Test
    void validateResourceNameLength_tooLong_throwsException() {
        String longName = "a".repeat(256);
        assertThatThrownBy(() -> CurriculumRequestNormalizer.validateResourceNameLength(longName))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("255 characters");
    }

    @Test
    void validateResourceNameLength_exactly255_doesNotThrow() {
        String name = "a".repeat(255);
        CurriculumRequestNormalizer.validateResourceNameLength(name);
    }
}
