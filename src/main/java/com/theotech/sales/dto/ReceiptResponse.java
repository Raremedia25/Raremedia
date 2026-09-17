package com.theotech.sales.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** One receipt: the lines sold together, their total, and the payment state (all lines move together). */
public record ReceiptResponse(
        String receiptNo,
        Instant soldAt,
        String soldByName,
        boolean paid,
        Instant paidAt,
        String customerName,
        List<SaleResponse> lines,
        int itemCount,
        BigDecimal total) {

    public static ReceiptResponse of(List<SaleResponse> lines) {
        SaleResponse first = lines.getFirst();
        int items = lines.stream().mapToInt(SaleResponse::quantity).sum();
        BigDecimal total = lines.stream().map(SaleResponse::total).reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean paid = lines.stream().allMatch(SaleResponse::paid);
        return new ReceiptResponse(first.receiptNo(), first.soldAt(), first.soldByName(), paid, paid ? first.paidAt() : null,
                first.customerName(), lines, items, total);
    }
}
