package com.theotech.sales.dto;

import java.math.BigDecimal;

/** Sales of one product summed over a period (JPQL constructor projection); {@code unpaidTotal} = still owed. */
public record ProductSalesAggregate(Long productId, String productName, Long quantity, BigDecimal total, BigDecimal unpaidTotal) {
}
