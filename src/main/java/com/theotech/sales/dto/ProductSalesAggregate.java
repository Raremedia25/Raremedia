package com.theotech.sales.dto;

import java.math.BigDecimal;

/** Sales of one product summed over a period (JPQL constructor projection). */
public record ProductSalesAggregate(Long productId, String productName, Long quantity, BigDecimal total) {
}
