package com.koru.notification.service;

import com.koru.notification.domain.ProcessedOrderEvent;
import com.koru.notification.event.OrderPlacedEvent;
import com.koru.notification.repository.ProcessedOrderEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class EventClaimServiceTest {

    @Autowired
    private EventClaimService eventClaimService;

    @Autowired
    private ProcessedOrderEventRepository processedOrderEventRepository;

    @Test
    void secondClaimOfSameEventIdIsRejected() {
        OrderPlacedEvent event = new OrderPlacedEvent(
                "evt-service-test-1", 99L, "KORU-TKL-01", "Koru TKL Keyboard", 1, Instant.now());

        boolean firstClaim = eventClaimService.tryClaim(event);
        boolean secondClaim = eventClaimService.tryClaim(event);

        assertTrue(firstClaim, "First claim should succeed");
        assertFalse(secondClaim, "Second claim of the same eventId should be rejected");
    }

    @Test
    void markDonePersistsStatusChange() {
        OrderPlacedEvent event = new OrderPlacedEvent(
                "evt-service-test-2", 100L, "KORU-TKL-01", "Koru TKL Keyboard", 1, Instant.now());

        eventClaimService.tryClaim(event);
        eventClaimService.markDone(event.eventId());

        ProcessedOrderEvent saved = processedOrderEventRepository.findById(event.eventId()).orElseThrow();
        assertEquals(ProcessedOrderEvent.Status.DONE, saved.getStatus());
    }
}
