package com.koru.order.service;

public class OrderNotFoundException extends NotFoundException {
    public OrderNotFoundException(Long id) {
        super("No order found with id " + id);
    }
}
