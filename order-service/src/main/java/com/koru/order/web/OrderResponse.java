package com.koru.order.web;

import com.koru.order.domain.Order;
import com.koru.order.domain.OrderStatus;

import java.time.Instant;

public record OrderResponse(
        Long id,
        String sku,
        int quantity,
        OrderStatus status,
        Instant createdAt
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getProduct().getSku(),
                order.getQuantity(),
                order.getStatus(),
                order.getCreatedAt()
        );
    }
}
