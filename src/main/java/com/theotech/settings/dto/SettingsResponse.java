package com.theotech.settings.dto;

/** The handful of settings the shop actually needs. {@code logoUrl} is null when no logo has been uploaded. */
public record SettingsResponse(String companyName, String companyAddress, String companyPhone, int lowStockThreshold, String logoUrl) {
}
