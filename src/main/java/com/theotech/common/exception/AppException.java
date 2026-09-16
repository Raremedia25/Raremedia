package com.theotech.common.exception;

import org.springframework.http.HttpStatus;

import java.util.Map;

/**
 * Base class for every business exception. Carries a stable {@code code} that the frontend
 * translates (the {@code error.*} i18n namespace) and the HTTP status to answer with.
 */
public class AppException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final transient Map<String, String> errors;

    public AppException(HttpStatus status, String code, String message) {
        this(status, code, message, null);
    }

    public AppException(HttpStatus status, String code, String message, Map<String, String> errors) {
        super(message);
        this.status = status;
        this.code = code;
        this.errors = errors;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public Map<String, String> getErrors() {
        return errors;
    }
}
