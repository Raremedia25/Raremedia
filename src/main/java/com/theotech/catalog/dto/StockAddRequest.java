package com.theotech.catalog.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** "+ Add Stock": how many more units were put on the shelf. */
public record StockAddRequest(@NotNull(message = "required") @Min(value = 1, message = "positive") Integer quantity) {
}
