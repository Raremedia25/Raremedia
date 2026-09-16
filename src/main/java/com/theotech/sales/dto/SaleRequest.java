package com.theotech.sales.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** "Sell 3 of product 5". The price comes from the product, never from the client. */
public record SaleRequest(
        @NotNull(message = "required") Long productId,
        @NotNull(message = "required") @Min(value = 1, message = "positive") Integer quantity) {
}
