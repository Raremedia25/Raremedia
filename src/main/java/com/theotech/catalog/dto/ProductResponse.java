package com.theotech.catalog.dto;

import com.theotech.catalog.domain.Product;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * What every page shows about a product. {@code status} depends on the shop's low-stock level;
 * {@code imageUrl} is null when the product has no picture (the URL carries a version so browsers
 * can cache pictures and still pick up a replacement immediately).
 */
public record ProductResponse(
        Long id,
        String name,
        Long categoryId,
        String categoryName,
        BigDecimal price,
        int initialStock,
        int soldQuantity,
        int availableStock,
        String status,
        String imageUrl,
        Instant createdAt,
        Instant updatedAt) {

    public static final String OUT_OF_STOCK = "OUT_OF_STOCK";
    public static final String LOW = "LOW";
    public static final String AVAILABLE = "AVAILABLE";

    public static ProductResponse from(Product p, int lowStockThreshold) {
        int available = p.getAvailableStock();
        return new ProductResponse(p.getId(), p.getName(), p.getCategory().getId(), p.getCategory().getName(),
                p.getPrice(), p.getInitialStock(), p.getSoldQuantity(), available, status(available, lowStockThreshold),
                imageUrl(p), p.getCreatedAt(), p.getUpdatedAt());
    }

    public static String status(int available, int lowStockThreshold) {
        if (available <= 0) return OUT_OF_STOCK;
        if (available <= lowStockThreshold) return LOW;
        return AVAILABLE;
    }

    public static String imageUrl(Product p) {
        return p.hasImage() ? "/api/products/" + p.getId() + "/image?v=" + p.getImageUpdatedAt().toEpochMilli() : null;
    }
}
