package com.theotech.dashboard.dto;

import com.theotech.catalog.dto.ProductResponse;
import com.theotech.sales.dto.SaleResponse;

import java.math.BigDecimal;
import java.util.List;

/** Everything the dashboard shows; every number is a live query. */
public record DashboardResponse(
        long totalProducts,
        long itemsInStock,
        long itemsSold,
        BigDecimal totalSales,
        int lowStockThreshold,
        List<ProductResponse> lowStock,
        List<ProductResponse> outOfStock,
        List<SaleResponse> recentSales) {
}
