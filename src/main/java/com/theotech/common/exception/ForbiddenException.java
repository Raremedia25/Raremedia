package com.theotech.common.exception;

import org.springframework.http.HttpStatus;

/** The caller is authenticated but not allowed to do this. HTTP 403. */
public class ForbiddenException extends AppException {

    public ForbiddenException(String code, String message) {
        super(HttpStatus.FORBIDDEN, code, message);
    }

    public ForbiddenException(String message) {
        this("FORBIDDEN", message);
    }
}
