package com.theotech.catalog.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** The whole product form: name, category, price, stock. Validation messages are keys the UI turns into text. */
public record ProductRequest(
        @NotBlank(message = "required") @Size(max = 120, message = "maxLength") String name,
        @NotNull(message = "required") Long categoryId,
        @NotNull(message = "required") @DecimalMin(value = "0", message = "min")
        @Digits(integer = 12, fraction = 2, message = "invalid") BigDecimal price,
        @Min(value = 0, message = "min") Integer initialStock) {
}
