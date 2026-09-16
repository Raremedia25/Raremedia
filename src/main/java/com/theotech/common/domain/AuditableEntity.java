package com.theotech.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;

import java.time.Instant;

/** Timestamps plus created_by / updated_by (user ids), soft-delete marker and optimistic-lock version. */
@MappedSuperclass
public abstract class AuditableEntity extends TimestampedEntity {

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @LastModifiedBy
    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public Long getCreatedBy() {
        return createdBy;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void markDeleted() {
        this.deletedAt = Instant.now();
    }

    public long getVersion() {
        return version;
    }
}
