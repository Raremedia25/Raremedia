package com.theotech.sales.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * One sale = one receipt with one or more items. Either {@code items} (the cart) or the single
 * {@code productId}/{@code quantity} pair. Prices come from the products, never from the client.
 * {@code paid} defaults to true (cash on the spot); an unpaid sale needs the customer's name.
 */
public record SaleRequest(
        Long productId,
        @Min(value = 1, message = "positive") Integer quantity,
        @Size(max = 100, message = "maxLength") List<@Valid Item> items,
        Boolean paid,
        @Size(max = 120, message = "maxLength") String customerName) {

    public record Item(
            @NotNull(message = "required") Long productId,
            @NotNull(message = "required") @Min(value = 1, message = "positive") Integer quantity) {
    }

    public boolean isPaid() {
        return paid == null || paid;
    }

    /** The cart, or the single pair wrapped as one item (with nulls kept so the service can report them). */
    public List<Item> lines() {
        if (items != null && !items.isEmpty()) return items;
        if (productId == null && quantity == null) return List.of();
        return List.of(new Item(productId, quantity));
    }
}
