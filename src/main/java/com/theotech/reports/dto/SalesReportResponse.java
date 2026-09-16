package com.theotech.reports.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** The one report: what sold in a period, for how much, and what is left of it. */
public record SalesReportResponse(
        Instant from,
        Instant to,
        List<Row> rows,
        long totalQuantity,
        BigDecimal totalSales) {

    /** {@code remainingStock} is null when the product has since been deleted. */
    public record Row(Long productId, String productName, String categoryName, long quantitySold,
                      BigDecimal totalSales, Integer remainingStock) {
    }
}
