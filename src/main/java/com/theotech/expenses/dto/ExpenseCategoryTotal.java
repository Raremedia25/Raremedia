package com.theotech.expenses.dto;

import java.math.BigDecimal;

/** Expenses of one category summed over a period (JPQL constructor projection). */
public record ExpenseCategoryTotal(String category, Long count, BigDecimal total) {
}
