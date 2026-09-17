package com.theotech.expenses.dto;

import com.theotech.common.PageResponse;

import java.math.BigDecimal;
import java.util.List;

/** A page of expenses plus the total of the whole filtered set (same shape as PageResponse). */
public record ExpensesPageResponse(
        List<ExpenseResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last,
        BigDecimal totalAmount) {

    public static ExpensesPageResponse of(PageResponse<ExpenseResponse> p, BigDecimal totalAmount) {
        return new ExpensesPageResponse(p.content(), p.page(), p.size(), p.totalElements(), p.totalPages(),
                p.first(), p.last(), totalAmount);
    }
}
