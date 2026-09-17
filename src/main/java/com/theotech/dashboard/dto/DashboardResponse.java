package com.theotech.dashboard.dto;

import com.theotech.catalog.dto.ProductResponse;
import com.theotech.sales.dto.SaleResponse;

import java.math.BigDecimal;
import java.util.List;

/**
 * Everything the dashboard shows; every number is a live query. {@code unpaid*} = sales not yet paid for;
 * {@code totalExpenses} is null for workers (money the shop spends is the administrator's business).
 */
public record DashboardResponse(
        long totalProducts,
        long itemsInStock,
        long itemsSold,
        BigDecimal totalSales,
        long unpaidCount,
        BigDecimal unpaidAmount,
        BigDecimal totalExpenses,
        int lowStockThreshold,
        List<ProductResponse> lowStock,
        List<ProductResponse> outOfStock,
        List<SaleResponse> recentSales) {
}
