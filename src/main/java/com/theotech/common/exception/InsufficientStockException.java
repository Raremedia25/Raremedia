package com.theotech.common.exception;

import org.springframework.http.HttpStatus;

import java.util.Map;

/** A stock change would drive a product below zero and the caller is not allowed to do that. HTTP 409. */
public class InsufficientStockException extends AppException {

    public InsufficientStockException(Long productId, int available, int requested) {
        super(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK",
                "Insufficient stock for product " + productId + ": available " + available + ", requested " + requested,
                Map.of("productId", String.valueOf(productId), "available", String.valueOf(available),
                       "requested", String.valueOf(requested)));
    }
}
