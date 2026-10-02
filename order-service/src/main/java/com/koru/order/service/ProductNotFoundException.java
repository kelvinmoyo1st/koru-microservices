package com.koru.order.service;

public class ProductNotFoundException extends NotFoundException {
    public ProductNotFoundException(String sku) {
        super("No product found for SKU " + sku);
    }
}
