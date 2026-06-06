package com.smartlearnly.backend.common.api;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(
        boolean success,
        int status,
        String code,
        String message,
        String path,
        List<FieldErrorDetail> errors,
        Instant timestamp
) {
    public static ErrorResponse of(int status, String code, String message, String path) {
        return new ErrorResponse(false, status, code, message, path, List.of(), Instant.now());
    }

    public static ErrorResponse of(
            int status,
            String code,
            String message,
            String path,
            List<FieldErrorDetail> errors
    ) {
        return new ErrorResponse(false, status, code, message, path, errors, Instant.now());
    }

    public record FieldErrorDetail(String field, String message) {
    }
}
