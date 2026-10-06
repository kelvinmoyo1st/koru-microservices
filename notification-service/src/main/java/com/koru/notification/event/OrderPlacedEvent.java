package com.koru.notification.event;

import java.time.Instant;

public record OrderPlacedEvent(
        String eventId,
        Long orderId,
        String sku,
        String productName,
        int quantity,
        Instant placedAt
) {}
