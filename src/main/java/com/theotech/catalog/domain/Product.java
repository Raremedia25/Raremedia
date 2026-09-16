package com.theotech.catalog.domain;

import com.theotech.common.domain.AuditableEntity;
import com.theotech.common.exception.InsufficientStockException;
import com.theotech.common.exception.ValidationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Map;

/**
 * An electronic item the shop sells. Two counters tell the whole stock story:
 * <pre>
 *   initialStock  = everything ever put on the shelf (opening stock + every "Add stock")
 *   soldQuantity  = everything sold
 *   available     = initialStock - soldQuantity     (computed, never stored)
 * </pre>
 * The database enforces {@code sold_quantity <= initial_stock}, so stock can never go negative even
 * if a bug slipped past the checks here. {@code imageUpdatedAt} is set while a picture exists.
 */
@Entity
@Table(name = "products")
public class Product extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal price;

    @Column(name = "initial_stock", nullable = false)
    private int initialStock;

    @Column(name = "sold_quantity", nullable = false)
    private int soldQuantity;

    @Column(name = "image_updated_at")
    private Instant imageUpdatedAt;

    protected Product() {
    }

    public Product(String name, Category category, BigDecimal price, int initialStock) {
        this.name = name;
        this.category = category;
        setPrice(price);
        this.initialStock = initialStock;
    }

    public int getAvailableStock() {
        return initialStock - soldQuantity;
    }

    /** "+ Add Stock": more units put on the shelf. */
    public void addStock(int quantity) {
        if (quantity <= 0) {
            throw new ValidationException("Quantity must be positive", Map.of("quantity", "positive"));
        }
        this.initialStock += quantity;
    }

    /** Sells units; refuses to go below zero. */
    public void sell(int quantity) {
        if (quantity <= 0) {
            throw new ValidationException("Quantity must be positive", Map.of("quantity", "positive"));
        }
        int available = getAvailableStock();
        if (quantity > available) {
            throw new InsufficientStockException(id, available, quantity);
        }
        this.soldQuantity += quantity;
    }

    /** Direct correction of the stocked total (edit form). Cannot drop below what was already sold. */
    public void setInitialStock(int initialStock) {
        if (initialStock < 0) {
            throw new ValidationException("Stock must be zero or more", Map.of("initialStock", "min"));
        }
        if (initialStock < soldQuantity) {
            throw new ValidationException("Stock cannot be lower than the quantity already sold (" + soldQuantity + ")",
                    Map.of("initialStock", "belowSold"));
        }
        this.initialStock = initialStock;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = (price == null ? BigDecimal.ZERO : price).setScale(2, RoundingMode.HALF_UP);
    }

    public int getInitialStock() {
        return initialStock;
    }

    public int getSoldQuantity() {
        return soldQuantity;
    }

    public Instant getImageUpdatedAt() {
        return imageUpdatedAt;
    }

    public void setImageUpdatedAt(Instant imageUpdatedAt) {
        this.imageUpdatedAt = imageUpdatedAt;
    }

    public boolean hasImage() {
        return imageUpdatedAt != null;
    }
}
