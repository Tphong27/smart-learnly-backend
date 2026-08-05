package com.smartlearnly.backend.curriculum.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.curriculum.dto.LessonRequest;
import com.smartlearnly.backend.learning.lesson.entity.LessonStatus;
import com.smartlearnly.backend.learning.lesson.entity.LessonType;
import java.util.List;
import org.junit.jupiter.api.Test;

class CurriculumParseServiceTest {

    @Test
    void parseLessonType_nullValue_returnsDefault() {
        LessonType result = CurriculumParseService.parseLessonType((String) null, LessonType.VIDEO);
        assertThat(result).isEqualTo(LessonType.VIDEO);
    }

    @Test
    void parseLessonType_blankValue_returnsDefault() {
        LessonType result = CurriculumParseService.parseLessonType("   ", LessonType.PDF);
        assertThat(result).isEqualTo(LessonType.PDF);
    }

    @Test
    void parseLessonType_validValue_parsesCorrectly() {
        LessonType result = CurriculumParseService.parseLessonType("VIDEO", LessonType.PDF);
        assertThat(result).isEqualTo(LessonType.VIDEO);
    }

    @Test
    void parseLessonType_caseInsensitive_parsesCorrectly() {
        LessonType result = CurriculumParseService.parseLessonType("video", LessonType.PDF);
        assertThat(result).isEqualTo(LessonType.VIDEO);

        LessonType result2 = CurriculumParseService.parseLessonType("Video", LessonType.PDF);
        assertThat(result2).isEqualTo(LessonType.VIDEO);
    }

    @Test
    void parseLessonType_invalidValue_throwsException() {
        assertThatThrownBy(() -> CurriculumParseService.parseLessonType("invalid", LessonType.VIDEO))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Lesson type must be");
    }

    @Test
    void parseLessonTypeFromRequest_nullLessonTypeAndType_usesDefault() {
        LessonRequest request = new LessonRequest(
                "Title", null, null, null, null, null, null, null, null, null, null);
        LessonType result = CurriculumParseService.parseLessonType(request, LessonType.VIDEO);
        assertThat(result).isEqualTo(LessonType.VIDEO);
    }

    @Test
    void parseLessonTypeFromRequest_lessonTypePresent_usesLessonType() {
        LessonRequest request = new LessonRequest(
                "Title", "RICH_TEXT", null, null, null, null, null, null, null, null, null);
        LessonType result = CurriculumParseService.parseLessonType(request, LessonType.VIDEO);
        assertThat(result).isEqualTo(LessonType.RICH_TEXT);
    }

    @Test
    void parseLessonTypeFromRequest_typePresent_usesType() {
        LessonRequest request = new LessonRequest(
                "Title", null, "PDF", null, null, null, null, null, null, null, null);
        LessonType result = CurriculumParseService.parseLessonType(request, LessonType.VIDEO);
        assertThat(result).isEqualTo(LessonType.PDF);
    }

    @Test
    void parseLessonTypeFromRequest_lessonTypeTakesPrecedence() {
        LessonRequest request = new LessonRequest(
                "Title", "VIDEO", "PDF", null, null, null, null, null, null, null, null);
        LessonType result = CurriculumParseService.parseLessonType(request, LessonType.RICH_TEXT);
        assertThat(result).isEqualTo(LessonType.VIDEO);
    }

    @Test
    void parseLessonStatus_nullValue_returnsDefault() {
        LessonStatus result = CurriculumParseService.parseLessonStatus(null, LessonStatus.PUBLISHED);
        assertThat(result).isEqualTo(LessonStatus.PUBLISHED);
    }

    @Test
    void parseLessonStatus_blankValue_returnsDefault() {
        LessonStatus result = CurriculumParseService.parseLessonStatus("   ", LessonStatus.DRAFT);
        assertThat(result).isEqualTo(LessonStatus.DRAFT);
    }

    @Test
    void parseLessonStatus_validValue_parsesCorrectly() {
        LessonStatus result = CurriculumParseService.parseLessonStatus("DRAFT", LessonStatus.PUBLISHED);
        assertThat(result).isEqualTo(LessonStatus.DRAFT);
    }

    @Test
    void parseLessonStatus_caseInsensitive_parsesCorrectly() {
        LessonStatus result = CurriculumParseService.parseLessonStatus("published", LessonStatus.DRAFT);
        assertThat(result).isEqualTo(LessonStatus.PUBLISHED);
    }

    @Test
    void parseLessonStatus_invalidValue_throwsException() {
        assertThatThrownBy(() -> CurriculumParseService.parseLessonStatus("invalid", LessonStatus.DRAFT))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Lesson status must be");
    }
}
