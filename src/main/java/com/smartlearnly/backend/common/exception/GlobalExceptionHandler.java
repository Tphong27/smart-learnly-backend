package com.smartlearnly.backend.common.exception;

import com.smartlearnly.backend.common.api.ErrorResponse;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException exception) {
        ErrorCode errorCode = exception.errorCode();
        return ResponseEntity
                .status(errorCode.status())
                .body(ErrorResponse.of(errorCode.name(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException exception) {
        List<ErrorResponse.FieldErrorDetail> errors = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::toFieldErrorDetail)
                .toList();

        ErrorCode errorCode = ErrorCode.VALIDATION_FAILED;
        return ResponseEntity
                .status(errorCode.status())
                .body(ErrorResponse.of(errorCode.name(), errorCode.defaultMessage(), errors));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception exception) {
        ErrorCode errorCode = ErrorCode.INTERNAL_ERROR;
        return ResponseEntity
                .status(errorCode.status())
                .body(ErrorResponse.of(errorCode.name(), errorCode.defaultMessage()));
    }

    private ErrorResponse.FieldErrorDetail toFieldErrorDetail(FieldError fieldError) {
        return new ErrorResponse.FieldErrorDetail(fieldError.getField(), fieldError.getDefaultMessage());
    }
}
