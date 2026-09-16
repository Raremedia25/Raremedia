package com.theotech.settings.dto;

/** The handful of settings the shop actually needs. */
public record SettingsResponse(String companyName, String companyAddress, String companyPhone, int lowStockThreshold) {
}
