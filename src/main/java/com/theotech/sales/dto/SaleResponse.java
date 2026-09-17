package com.theotech.sales.dto;

import com.theotech.sales.domain.Sale;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One line of a receipt / one sales-history row. {@code soldByName} is who recorded it (admin or worker);
 * {@code remainingStock} is only filled right after a sale (what is left to sell).
 */
public record SaleResponse(
        Long id,
        String receiptNo,
        Long productId,
        String productName,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal total,
        Instant soldAt,
        String soldByName,
        boolean paid,
        Instant paidAt,
        String customerName,
        Integer remainingStock) {

    public static SaleResponse from(Sale s, String soldByName, Integer remainingStock) {
        return new SaleResponse(s.getId(), s.receiptNo(), s.getProductId(), s.getProductName(), s.getQuantity(),
                s.getUnitPrice(), s.getTotal(), s.getSoldAt(), soldByName, s.isPaid(), s.getPaidAt(),
                s.getCustomerName(), remainingStock);
    }
}
