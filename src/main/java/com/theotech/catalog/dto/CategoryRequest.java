package com.theotech.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryRequest(@NotBlank(message = "required") @Size(max = 60, message = "maxLength") String name) {
}
