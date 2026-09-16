package com.theotech.settings.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SettingsRequest(
        @NotBlank(message = "required") @Size(max = 120, message = "maxLength") String companyName,
        @Size(max = 200, message = "maxLength") String companyAddress,
        @Size(max = 40, message = "maxLength") String companyPhone,
        @NotNull(message = "required") @Min(value = 0, message = "min") @Max(value = 100000, message = "invalid") Integer lowStockThreshold) {
}
