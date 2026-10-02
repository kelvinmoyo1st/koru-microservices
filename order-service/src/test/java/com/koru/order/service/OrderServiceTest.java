package com.koru.order.service;

import com.koru.order.domain.Order;
import com.koru.order.domain.Product;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private StockReservationService stockReservationService;

    @InjectMocks
    private OrderService orderService;

    @Test
    void retriesOnConflictAndSucceedsOnThirdAttempt() {
        Product product = new Product("Koru TKL Keyboard", "KORU-TKL-01", 12999, 5);
        Order expected = new Order(product, 1);
        expected.markConfirmed();

        when(stockReservationService.reserve("KORU-TKL-01", 1))
                .thenThrow(new ObjectOptimisticLockingFailureException(Product.class, 1L))
                .thenThrow(new ObjectOptimisticLockingFailureException(Product.class, 1L))
                .thenReturn(expected);

        Order result = orderService.placeOrder("KORU-TKL-01", 1);

        assertSame(expected, result);
        verify(stockReservationService, times(3)).reserve("KORU-TKL-01", 1);
    }

    @Test
    void givesUpAfterMaxAttemptsAndPropagatesException() {
        when(stockReservationService.reserve("KORU-TKL-01", 1))
                .thenThrow(new ObjectOptimisticLockingFailureException(Product.class, 1L));

        assertThrows(ObjectOptimisticLockingFailureException.class,
                () -> orderService.placeOrder("KORU-TKL-01", 1));

        verify(stockReservationService, times(3)).reserve("KORU-TKL-01", 1);
    }
}
