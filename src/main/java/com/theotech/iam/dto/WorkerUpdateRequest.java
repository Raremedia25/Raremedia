package com.theotech.iam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record WorkerUpdateRequest(
        @NotBlank(message = "required") @Size(max = 120, message = "maxLength") String fullName,
        @NotNull(message = "required") Boolean enabled) {
}
