package com.theotech.sales.dto;

import jakarta.validation.constraints.NotNull;

/** {@code POST /api/sales/{id}/paid} body: mark the sale paid (true) or not paid (false). */
public record PaidRequest(@NotNull(message = "required") Boolean paid) {
}
