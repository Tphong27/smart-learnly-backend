package com.smartlearnly.backend.curriculum.util;

import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;

/**
 * Utility class for normalizing and validating request strings.
 * Pure functions with no dependencies or side effects.
 */
public final class CurriculumRequestNormalizer {

    private CurriculumRequestNormalizer() {}

    /**
     * Normalizes a required string value.
     *
     * @param value the value to normalize
     * @param message the error message if validation fails
     * @return the normalized non-null, non-blank string
     * @throws BusinessException if value is null or blank
     */
    public static String normalizeRequired(String value, String message) {
        String normalized = normalizeNullable(value);
        if (normalized == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, message);
        }
        return normalized;
    }

    /**
     * Normalizes a nullable string value.
     *
     * @param value the value to normalize
     * @return the trimmed string, or null if blank
     */
    public static String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    /**
     * Extracts filename from URL.
     *
     * @param url the URL to extract filename from
     * @return the filename or null if not extractable
     */
    public static String fileNameFromUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }

        // Remove fragment
        int fragmentIndex = url.indexOf('#');
        String withoutFragment = fragmentIndex < 0 ? url : url.substring(0, fragmentIndex);

        // Remove query string
        int queryIndex = withoutFragment.indexOf('?');
        String withoutQuery = queryIndex < 0 ? withoutFragment : withoutFragment.substring(0, queryIndex);

        // Extract filename
        int slashIndex = withoutQuery.lastIndexOf('/');
        String filename = slashIndex < 0 ? withoutQuery : withoutQuery.substring(slashIndex + 1);

        return normalizeNullable(filename);
    }

    /**
     * Validates resource name length.
     *
     * @param name the name to validate
     * @throws BusinessException if name exceeds 255 characters
     */
    public static void validateResourceNameLength(String name) {
        if (name != null && name.length() > 255) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "Resource name must not exceed 255 characters");
        }
    }
}
