package com.theotech.settings.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "settings")
public class Setting {

    @Id
    @Column(name = "key", nullable = false)
    private String key;

    @Column(nullable = false)
    private String value;

    @Column(name = "value_type", nullable = false)
    private String valueType;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private boolean critical;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "updated_by")
    private Long updatedBy;

    protected Setting() {
    }

    public Setting(String key, String value, String valueType, String category, String description) {
        this.key = key;
        this.value = value;
        this.valueType = valueType;
        this.category = category;
        this.description = description;
    }

    public void update(String newValue, Long byUserId) {
        this.value = newValue;
        this.updatedBy = byUserId;
        this.updatedAt = Instant.now();
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public String getValueType() {
        return valueType;
    }

    public String getCategory() {
        return category;
    }

    public String getDescription() {
        return description;
    }

    public boolean isCritical() {
        return critical;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }
}
