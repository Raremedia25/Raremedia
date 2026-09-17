package com.theotech.reports.dto;

import com.theotech.expenses.dto.ExpenseCategoryTotal;
import com.theotech.sales.dto.SaleResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * The one report: what sold in a period, for how much, how much of it was paid, what is still owed
 * (and by whom), what is left of each product, and what the shop spent in the same period.
 * {@code netAfterExpenses} = total sales − total expenses (not a true profit: cost prices are not tracked).
 */
public record SalesReportResponse(
        Instant from,
        Instant to,
        List<Row> rows,
        long totalQuantity,
        BigDecimal totalSales,
        BigDecimal totalPaid,
        BigDecimal totalUnpaid,
        List<SaleResponse> unpaidSales,
        BigDecimal totalExpenses,
        BigDecimal netAfterExpenses,
        List<ExpenseCategoryTotal> expensesByCategory) {

    /** {@code remainingStock} is null when the product has since been deleted. */
    public record Row(Long productId, String productName, String categoryName, long quantitySold,
                      BigDecimal totalSales, BigDecimal unpaidSales, Integer remainingStock) {
    }
}
