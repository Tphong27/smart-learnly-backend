package com.smartlearnly.backend.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "Invalid request"),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Validation failed"),
    INVALID_OR_EXPIRED_TOKEN(HttpStatus.BAD_REQUEST, "Token is invalid or expired"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "HTTP method is not supported for this endpoint"),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type"),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Authentication is required"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Permission denied"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid credentials"),
    EMAIL_NOT_VERIFIED(HttpStatus.FORBIDDEN, "Email verification is required"),
    ACCOUNT_LOCKED(HttpStatus.LOCKED, "Account is temporarily locked"),
    ACCOUNT_INACTIVE(HttpStatus.FORBIDDEN, "Account is inactive"),
    RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "Category not found"),
    CONFLICT(HttpStatus.CONFLICT, "Resource conflict"),
    CATEGORY_SLUG_CONFLICT(HttpStatus.CONFLICT, "Category slug already exists"),
    CATEGORY_IN_USE(HttpStatus.CONFLICT, "Category is in use"),
    CATEGORY_HIERARCHY_INVALID(HttpStatus.UNPROCESSABLE_CONTENT, "Category hierarchy is invalid"),
    COURSE_NOT_ENROLLABLE(HttpStatus.UNPROCESSABLE_CONTENT, "Course is not available for enrollment"),
    COURSE_NOT_FREE(HttpStatus.UNPROCESSABLE_CONTENT, "Course is not free"),
    CLASS_NOT_AVAILABLE(HttpStatus.UNPROCESSABLE_CONTENT, "Class is not available for enrollment"),
    CLASS_FULL(HttpStatus.CONFLICT, "Class has reached capacity"),
    CLASS_CAPACITY_INVALID(HttpStatus.CONFLICT, "Class capacity is invalid"),
    INVALID_TRAINER(HttpStatus.UNPROCESSABLE_CONTENT, "Trainer is invalid"),
    PAYMENT_NOT_SUCCESSFUL(HttpStatus.CONFLICT, "Successful payment is required"),
    ENROLLMENT_TRANSITION_INVALID(HttpStatus.CONFLICT, "Enrollment transition is invalid"),
    ENROLLMENT_ACCESS_DENIED(HttpStatus.FORBIDDEN, "An active or completed enrollment is required"),
    COURSE_ACCESS_BLOCKED(HttpStatus.LOCKED, "Course access has been blocked"),
    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "Payload exceeds the allowed size"),
    BUSINESS_RULE_VIOLATION(HttpStatus.UNPROCESSABLE_CONTENT, "Business rule violation"),
    AI_SOURCE_NOT_RAG_READY(HttpStatus.UNPROCESSABLE_CONTENT, "AI source is not RAG-ready"),
    AI_SOURCE_OUT_OF_SCOPE(HttpStatus.FORBIDDEN, "AI source is outside the allowed scope"),
    AI_INVALID_GENERATION_CONFIG(HttpStatus.BAD_REQUEST, "AI generation config is invalid"),
    AI_BATCH_NOT_RETRYABLE(HttpStatus.CONFLICT, "AI batch is not retryable"),
    AI_DRAFT_VERSION_CONFLICT(HttpStatus.CONFLICT, "AI draft has been updated"),
    AI_DRAFT_INVALID(HttpStatus.UNPROCESSABLE_CONTENT, "AI draft is invalid"),
    AI_EVIDENCE_REQUIRED(HttpStatus.UNPROCESSABLE_CONTENT, "AI evidence review is required"),
    AI_EXACT_DUPLICATE_ACTIVE(HttpStatus.CONFLICT, "AI draft duplicates an active question"),
    AI_QUOTA_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "AI generation quota exceeded"),
    AI_PROVIDER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "AI provider is unavailable"),
    AI_PROVIDER_OUTPUT_INVALID(HttpStatus.UNPROCESSABLE_CONTENT, "AI provider output is invalid"),
    IMAGE_IMPORT_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Image import is unavailable"),
    EXTERNAL_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "External service unavailable"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
