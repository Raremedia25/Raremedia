package com.theotech.common.exception;

import org.springframework.http.HttpStatus;

public class NotFoundException extends AppException {

    public NotFoundException(String entity, Object id) {
        super(HttpStatus.NOT_FOUND, "NOT_FOUND", entity + " not found: " + id);
    }

    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }
}
