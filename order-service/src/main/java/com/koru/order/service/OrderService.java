package com.koru.order.service;

import com.koru.order.domain.Order;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

    private static final int MAX_ATTEMPTS = 3;

    private final StockReservationService stockReservationService;

    public OrderService(StockReservationService stockReservationService) {
        this.stockReservationService = stockReservationService;
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
                // loop again — the next call to reserve() opens a brand new
                // transaction and re-reads the product, picking up the latest version.
            }
        }
    }
}
