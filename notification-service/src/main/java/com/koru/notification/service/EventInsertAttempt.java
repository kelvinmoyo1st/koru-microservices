package com.koru.notification.service;

import com.koru.notification.domain.ProcessedOrderEvent;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
class EventInsertAttempt {

    private final EntityManager entityManager;

    EventInsertAttempt(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void insert(ProcessedOrderEvent entity) {
        entityManager.persist(entity);
        entityManager.flush();
    }
}
