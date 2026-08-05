package com.smartlearnly.backend.curriculum.util;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.curriculum.dto.LessonRequest;
import com.smartlearnly.backend.learning.lesson.entity.LessonStatus;
import com.smartlearnly.backend.learning.lesson.entity.LessonType;
import java.util.Locale;

/**
 * Parses and validates lesson-related enums from request strings.
 * Pure parsing logic with no database or service dependencies.
 */
public final class CurriculumParseService {

    private CurriculumParseService() {}

    /**
     * Parses lesson type from request.
     *
     * @param request the lesson request
     * @param defaultType the default type if not specified
     * @return the parsed lesson type
     */
    public static LessonType parseLessonType(LessonRequest request, LessonType defaultType) {
        String value = resolveLessonType(request);
        return parseLessonType(value, defaultType);
    }

    /**
     * Parses lesson type from string value.
     *
     * @param value the string value
     * @param defaultType the default type if not specified
     * @return the parsed lesson type
     */
    public static LessonType parseLessonType(String value, LessonType defaultType) {
        if (value == null || value.isBlank()) {
            return defaultType;
        }
        try {
            return LessonType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Lesson type must be video, pdf, rich_text, quiz, flashcard, assignment, or essay"
            );
        }
    }

    /**
     * Resolves lesson type from request (checks lessonType first, then type).
     */
    public static String resolveLessonType(LessonRequest request) {
        String lessonType = CurriculumRequestNormalizer.normalizeNullable(request.lessonType());
        return lessonType == null
                ? CurriculumRequestNormalizer.normalizeNullable(request.type())
                : lessonType;
    }

    /**
     * Parses lesson status from string value.
     *
     * @param value the string value
     * @param defaultStatus the default status if not specified
     * @return the parsed lesson status
     */
    public static LessonStatus parseLessonStatus(String value, LessonStatus defaultStatus) {
        if (value == null || value.isBlank()) {
            return defaultStatus;
        }
        try {
            return LessonStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Lesson status must be draft, published, or inactive"
            );
        }
    }
}
