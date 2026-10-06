package com.koru.notification.service;

import com.koru.notification.domain.ProcessedOrderEvent;
import com.koru.notification.event.OrderPlacedEvent;
import com.koru.notification.repository.ProcessedOrderEventRepository;
import jakarta.persistence.PersistenceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventClaimService {

    private final EventInsertAttempt eventInsertAttempt;
    private final ProcessedOrderEventRepository processedOrderEventRepository;

    public EventClaimService(EventInsertAttempt eventInsertAttempt,
                              ProcessedOrderEventRepository processedOrderEventRepository) {
        this.eventInsertAttempt = eventInsertAttempt;
        this.processedOrderEventRepository = processedOrderEventRepository;
    }

    public boolean tryClaim(OrderPlacedEvent event) {
        try {
            eventInsertAttempt.insert(new ProcessedOrderEvent(
                    event.eventId(), event.orderId(), event.sku(), event.productName(), event.quantity()));
            return true;
        } catch (PersistenceException duplicate) {
            return false;
        }
    }

    @Transactional
    public void markDone(String eventId) {
        processedOrderEventRepository.findById(eventId)
                .ifPresent(ProcessedOrderEvent::markDone);
    }
}
