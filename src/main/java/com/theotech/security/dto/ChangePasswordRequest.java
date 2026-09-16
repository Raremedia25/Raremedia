package com.theotech.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Validation messages are i18n keys under {@code validation.*}, translated by the frontend. */
public record ChangePasswordRequest(
        @NotBlank(message = "required") String currentPassword,
        @NotBlank(message = "required") @Size(min = 8, max = 100, message = "passwordLength") String newPassword) {
}
