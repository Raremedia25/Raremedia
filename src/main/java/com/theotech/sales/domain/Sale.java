package com.theotech.sales.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * One sale of one product. Immutable once written: it snapshots the product name and the price that
 * applied at the time, so history stays correct when the product is renamed, re-priced or deleted.
 */
@Entity
@Table(name = "sales")
public class Sale {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false, updatable = false)
    private Long productId;

    @Column(name = "product_name", nullable = false, updatable = false)
    private String productName;

    @Column(nullable = false, updatable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal unitPrice;

    @Column(nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal total;

    @Column(name = "sold_at", nullable = false, updatable = false)
    private Instant soldAt;

    @Column(name = "sold_by", updatable = false)
    private Long soldBy;

    protected Sale() {
    }

    public Sale(Long productId, String productName, int quantity, BigDecimal unitPrice, Long soldBy) {
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
        this.unitPrice = unitPrice.setScale(2, RoundingMode.HALF_UP);
        this.total = this.unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
        this.soldAt = Instant.now();
        this.soldBy = soldBy;
    }

    public Long getId() {
        return id;
    }

    public Long getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public Instant getSoldAt() {
        return soldAt;
    }

    public Long getSoldBy() {
        return soldBy;
    }
}
