package com.theotech.expenses.dto;

import com.theotech.expenses.domain.Expense;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record ExpenseResponse(
        Long id,
        LocalDate spentOn,
        String category,
        String description,
        BigDecimal amount,
        String note,
        String recordedByName,
        Instant createdAt,
        Instant updatedAt) {

    public static ExpenseResponse from(Expense e, String recordedByName) {
        return new ExpenseResponse(e.getId(), e.getSpentOn(), e.getCategory(), e.getDescription(), e.getAmount(),
                e.getNote(), recordedByName, e.getCreatedAt(), e.getUpdatedAt());
    }
}
