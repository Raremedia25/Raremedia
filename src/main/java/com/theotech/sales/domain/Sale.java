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
 * One line of a sale: one product, a quantity, the price that applied. Lines sold together share a
 * {@code receiptNo} (one ticket). What was sold never changes once written: it snapshots the product name
 * and price, so history stays correct when the product is renamed, re-priced or deleted. The only thing
 * that moves afterwards is {@code paid} (a customer who took goods on credit settles later).
 */
@Entity
@Table(name = "sales")
public class Sale {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "receipt_no", nullable = false, updatable = false)
    private Long receiptNo;

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

    @Column(nullable = false)
    private boolean paid = true;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "customer_name")
    private String customerName;

    protected Sale() {
    }

    public Sale(long receiptNo, Instant soldAt, Long productId, String productName, int quantity, BigDecimal unitPrice,
                Long soldBy, boolean paid, String customerName) {
        this.receiptNo = receiptNo;
        this.soldAt = soldAt;
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
        this.unitPrice = unitPrice.setScale(2, RoundingMode.HALF_UP);
        this.total = this.unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
        this.soldBy = soldBy;
        this.customerName = customerName;
        this.paid = paid;
        this.paidAt = paid ? soldAt : null;
    }

    /** Marks the line paid (money received) or unpaid (recorded by mistake as paid). */
    public void setPaid(boolean paid) {
        if (this.paid == paid) return;
        this.paid = paid;
        this.paidAt = paid ? Instant.now() : null;
    }

    /** Receipt number as printed on the ticket: zero-padded to six digits. */
    public String receiptNo() {
        return formatReceiptNo(receiptNo);
    }

    public static String formatReceiptNo(Long no) {
        return no == null ? null : String.format("%06d", no);
    }

    public Long getId() {
        return id;
    }

    public Long getReceiptNo() {
        return receiptNo;
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

    public boolean isPaid() {
        return paid;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public String getCustomerName() {
        return customerName;
    }
}
