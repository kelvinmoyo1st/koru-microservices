package com.koru.order.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CreateProductRequest(
        @NotBlank(message = "name must not be blank") String name,
        @NotBlank(message = "sku must not be blank") String sku,
        @Min(value = 1, message = "priceCents must be at least 1") long priceCents,
        @Min(value = 0, message = "stockQuantity cannot be negative") int stockQuantity
) {}
