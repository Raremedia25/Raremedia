package com.theotech.settings.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** The shop's logo: a single row (id = 1). */
@Entity
@Table(name = "shop_logo")
public class ShopLogo {

    public static final short SINGLETON_ID = 1;

    @Id
    private Short id = SINGLETON_ID;

    @Column(nullable = false)
    private byte[] content;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected ShopLogo() {
    }

    public ShopLogo(byte[] content, String contentType) {
        replace(content, contentType);
    }

    public void replace(byte[] newContent, String newContentType) {
        this.content = newContent;
        this.contentType = newContentType;
        this.updatedAt = Instant.now();
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
