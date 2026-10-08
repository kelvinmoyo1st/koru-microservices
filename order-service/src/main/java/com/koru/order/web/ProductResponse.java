package com.koru.order.web;

import com.koru.order.domain.Product;

public record ProductResponse(Long id, String name, String sku, long priceCents, int stockQuantity) {
    public static ProductResponse from(Product product) {
        return new ProductResponse(product.getId(), product.getName(), product.getSku(),
                product.getPriceCents(), product.getStockQuantity());
    }
}
