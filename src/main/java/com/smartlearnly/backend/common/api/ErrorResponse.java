package com.smartlearnly.backend.common.api;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(
        boolean success,
        String code,
        String message,
        List<FieldErrorDetail> errors,
        Instant timestamp
) {
    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(false, code, message, List.of(), Instant.now());
    }

    public static ErrorResponse of(String code, String message, List<FieldErrorDetail> errors) {
        return new ErrorResponse(false, code, message, errors, Instant.now());
    }

    public record FieldErrorDetail(String field, String message) {
    }
}
