package com.theotech.iam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank(message = "required") @Size(min = 8, max = 100, message = "passwordLength") String newPassword) {
}
