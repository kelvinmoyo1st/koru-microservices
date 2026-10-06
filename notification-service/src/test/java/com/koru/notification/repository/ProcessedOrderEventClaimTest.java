package com.koru.notification.repository;

import com.koru.notification.domain.ProcessedOrderEvent;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
class ProcessedOrderEventClaimTest {

    @Autowired
    private EntityManager entityManager;

    @Test
    void claimingTheSameEventIdTwiceThrows() {
        String eventId = "evt-duplicate-test";

        entityManager.persist(new ProcessedOrderEvent(eventId, 1L, "KORU-TKL-01", "Koru TKL Keyboard", 1));
        entityManager.flush();
        entityManager.clear();

        assertThrows(PersistenceException.class, () -> {
            entityManager.persist(new ProcessedOrderEvent(eventId, 1L, "KORU-TKL-01", "Koru TKL Keyboard", 1));
            entityManager.flush();
        });
    }
}
