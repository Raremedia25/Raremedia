package com.theotech.expenses.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Add or edit one expense. Validation messages are the short keys {@code js/ui.js} turns into text. */
public record ExpenseRequest(
        @NotNull(message = "required") LocalDate spentOn,
        @NotBlank(message = "required") @Size(max = 60, message = "maxLength") String category,
        @NotBlank(message = "required") @Size(max = 200, message = "maxLength") String description,
        @NotNull(message = "required") @DecimalMin(value = "0.01", message = "positive")
        @Digits(integer = 12, fraction = 2, message = "invalid") BigDecimal amount,
        @Size(max = 500, message = "maxLength") String note) {
}
