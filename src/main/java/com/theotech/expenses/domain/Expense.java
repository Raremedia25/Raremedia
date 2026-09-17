package com.theotech.expenses.domain;

import com.theotech.common.domain.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/** Money the shop spent: when, on what, how much. */
@Entity
@Table(name = "expenses")
public class Expense extends TimestampedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "spent_on", nullable = false)
    private LocalDate spentOn;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    private String note;

    @Column(name = "recorded_by", updatable = false)
    private Long recordedBy;

    protected Expense() {
    }

    public Expense(LocalDate spentOn, String category, String description, BigDecimal amount, String note, Long recordedBy) {
        this.recordedBy = recordedBy;
        update(spentOn, category, description, amount, note);
    }

    public void update(LocalDate spentOn, String category, String description, BigDecimal amount, String note) {
        this.spentOn = spentOn;
        this.category = category;
        this.description = description;
        this.amount = amount.setScale(2, RoundingMode.HALF_UP);
        this.note = note;
    }

    public Long getId() {
        return id;
    }

    public LocalDate getSpentOn() {
        return spentOn;
    }

    public String getCategory() {
        return category;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getNote() {
        return note;
    }

    public Long getRecordedBy() {
        return recordedBy;
    }
}
