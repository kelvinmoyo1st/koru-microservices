package com.koru.order.repository;

import com.koru.order.domain.Product;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
class ProductRepositoryOptimisticLockingTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void concurrentStockUpdatesTriggerOptimisticLockException() {
        Product saved = productRepository.saveAndFlush(
                new Product("Koru TKL Keyboard", "KORU-TKL-01", 12999, 1));
        Long id = saved.getId();
        entityManager.clear();

        Product requestA = productRepository.findById(id).orElseThrow();
        entityManager.clear();

        Product requestB = productRepository.findById(id).orElseThrow();
        entityManager.clear();

        requestA.decreaseStock(1);
        productRepository.saveAndFlush(requestA);
        entityManager.clear();

        requestB.decreaseStock(1);
        assertThrows(
                ObjectOptimisticLockingFailureException.class,
                () -> productRepository.saveAndFlush(requestB)
        );
    }
}
