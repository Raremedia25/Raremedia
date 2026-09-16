package com.theotech.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** The picture of one product, kept in its own table so product lists never load image bytes. */
@Entity
@Table(name = "product_images")
public class ProductImage {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(nullable = false)
    private byte[] content;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected ProductImage() {
    }

    public ProductImage(Long productId, byte[] content, String contentType) {
        this.productId = productId;
        replace(content, contentType);
    }

    public void replace(byte[] newContent, String newContentType) {
        this.content = newContent;
        this.contentType = newContentType;
        this.updatedAt = Instant.now();
    }

    public Long getProductId() {
        return productId;
    }

    public byte[] getContent() {
        return content;
    }

    public String getContentType() {
        return contentType;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
