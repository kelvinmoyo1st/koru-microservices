package com.koru.order.service;

import com.koru.order.domain.Order;
import com.koru.order.repository.OrderRepository;
import com.koru.order.web.OrderResponse;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private static final int MAX_ATTEMPTS = 3;

    private final StockReservationService stockReservationService;
    private final OrderRepository orderRepository;

    public OrderService(StockReservationService stockReservationService,
                         OrderRepository orderRepository) {
        this.stockReservationService = stockReservationService;
        this.orderRepository = orderRepository;
    }

    public Order placeOrder(String sku, int quantity) {
        int attempt = 0;
        while (true) {
            attempt++;
            try {
                return stockReservationService.reserve(sku, quantity);
            } catch (ObjectOptimisticLockingFailureException conflict) {
                if (attempt >= MAX_ATTEMPTS) {
                    throw conflict;
                }
            }
        }
    }

    @Transactional(readOnly = true)
    public OrderResponse findById(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
        return OrderResponse.from(order);
    }
}
