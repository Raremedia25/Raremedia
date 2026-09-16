package com.theotech.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * Standard API envelope (spec §42).
 * <pre>
 * { "success": true,  "data": {...}, "timestamp": "..." }
 * { "success": false, "code": "NOT_FOUND", "message": "Product not found", "timestamp": "..." }
 * { "success": false, "code": "VALIDATION_FAILED", "message": "...", "errors": { "name": "..." }, "timestamp": "..." }
 * </pre>
 * {@code code} is a stable machine-readable key the frontend translates; {@code message} is a developer hint
 * and is never shown to users verbatim.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        T data,
        String code,
        String message,
        Map<String, String> errors,
        Instant timestamp) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, null, null, Instant.now());
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(true, null, null, null, null, Instant.now());
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(false, null, code, message, null, Instant.now());
    }

    public static <T> ApiResponse<T> error(String code, String message, Map<String, String> errors) {
        return new ApiResponse<>(false, null, code, message, errors, Instant.now());
    }
}
