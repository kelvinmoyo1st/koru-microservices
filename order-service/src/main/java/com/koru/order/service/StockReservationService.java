package com.koru.order.service;

import com.koru.order.domain.Order;
import com.koru.order.domain.OutboxEvent;
import com.koru.order.domain.Product;
import com.koru.order.repository.OrderRepository;
import com.koru.order.repository.OutboxEventRepository;
import com.koru.order.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class StockReservationService {

    private static final String ORDER_PLACED_TOPIC = "order.placed";

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;

    public StockReservationService(ProductRepository productRepository,
                                    OrderRepository orderRepository,
                                    OutboxEventRepository outboxEventRepository) {
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.outboxEventRepository = outboxEventRepository;
    }

    @Transactional
    public Order reserve(String sku, int quantity) {
        Product product = productRepository.findBySku(sku)
                .orElseThrow(() -> new ProductNotFoundException(sku));

        product.decreaseStock(quantity);
        productRepository.save(product);

        Order order = new Order(product, quantity);
        order.markConfirmed();
        Order savedOrder = orderRepository.save(order);

        OutboxEvent outboxEvent = new OutboxEvent(
                UUID.randomUUID().toString(),
                ORDER_PLACED_TOPIC,
                savedOrder.getId(),
                product.getSku(),
                product.getName(),
                quantity,
                savedOrder.getCreatedAt()
        );
        outboxEventRepository.save(outboxEvent);

        return savedOrder;
    }
}
