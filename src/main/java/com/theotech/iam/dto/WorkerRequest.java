package com.theotech.iam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** New worker: name, login name and a first password (they must change it when they first sign in). */
public record WorkerRequest(
        @NotBlank(message = "required") @Size(max = 120, message = "maxLength") String fullName,
        @NotBlank(message = "required") @Pattern(regexp = "^[A-Za-z0-9._-]{3,40}$", message = "username") String username,
        @NotBlank(message = "required") @Size(min = 8, max = 100, message = "passwordLength") String password) {
}
