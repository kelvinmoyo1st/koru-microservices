package com.koru.order.service;

import com.koru.order.domain.Order;
import com.koru.order.domain.Product;
import com.koru.order.repository.OrderRepository;
import com.koru.order.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockReservationService {

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;

    public StockReservationService(ProductRepository productRepository,
                                    OrderRepository orderRepository) {
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
    }

    @Transactional
    public Order reserve(String sku, int quantity) {
        Product product = productRepository.findBySku(sku)
                .orElseThrow(() -> new ProductNotFoundException(sku));

        product.decreaseStock(quantity);
        productRepository.save(product);

        Order order = new Order(product, quantity);
        order.markConfirmed();
        return orderRepository.save(order);
    }
}
