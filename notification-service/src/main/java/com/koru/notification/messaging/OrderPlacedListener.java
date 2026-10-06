package com.koru.notification.messaging;

import com.koru.notification.event.OrderPlacedEvent;
import com.koru.notification.service.EventClaimService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderPlacedListener {

    private static final Logger log = LoggerFactory.getLogger(OrderPlacedListener.class);

    private final EventClaimService eventClaimService;

    public OrderPlacedListener(EventClaimService eventClaimService) {
        this.eventClaimService = eventClaimService;
    }

    @KafkaListener(topics = "order.placed", groupId = "notification-service")
    public void onOrderPlaced(OrderPlacedEvent event) {
        if (!eventClaimService.tryClaim(event)) {
            log.info("Duplicate OrderPlacedEvent ignored, eventId={}", event.eventId());
            return;
        }
        try {
            log.info("Notifying customer: order {} placed for {} x {} ({})",
                    event.orderId(), event.quantity(), event.productName(), event.sku());
            eventClaimService.markDone(event.eventId());
        } catch (Exception sendFailure) {
            log.warn("Notification send failed, eventId={} left PENDING for retry sweep", event.eventId());
        }
    }
}
