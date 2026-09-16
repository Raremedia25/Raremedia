package com.theotech.common.exception;

import org.springframework.http.HttpStatus;

import java.util.Map;

/** A business rule violation that maps to HTTP 400. */
public class ValidationException extends AppException {

    public ValidationException(String code, String message) {
        super(HttpStatus.BAD_REQUEST, code, message);
    }

    public ValidationException(String message, Map<String, String> fieldErrors) {
        super(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message, fieldErrors);
    }

    public static ValidationException field(String field, String code, String message) {
        return new ValidationException(message, Map.of(field, code));
    }
}
