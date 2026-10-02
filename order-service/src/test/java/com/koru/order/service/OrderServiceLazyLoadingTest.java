package com.koru.order.service;

import com.koru.order.domain.Order;
import com.koru.order.domain.Product;
import com.koru.order.repository.ProductRepository;
import com.koru.order.web.OrderResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class OrderServiceLazyLoadingTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderService orderService;

    @Test
    void findByIdInitializesProductWithinItsOwnTransaction() {
        Product product = productRepository.saveAndFlush(
                new Product("Koru 60% Keyboard", "KORU-60-01", 9999, 10));

        Order placed = orderService.placeOrder(product.getSku(), 1);

        OrderResponse response = orderService.findById(placed.getId());

        assertEquals("KORU-60-01", response.sku());
    }
}
