package com.theotech.common.exception;

import org.springframework.http.HttpStatus;

/** State conflicts: duplicates, insufficient stock, stale records. HTTP 409. */
public class ConflictException extends AppException {

    public ConflictException(String code, String message) {
        super(HttpStatus.CONFLICT, code, message);
    }
}
