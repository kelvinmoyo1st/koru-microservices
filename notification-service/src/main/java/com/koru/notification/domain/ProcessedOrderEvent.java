package com.koru.notification.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "processed_order_events")
public class ProcessedOrderEvent {

    public enum Status { PENDING, DONE }

    @Id
    private String eventId;

    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false)
    private String sku;

    @Column(nullable = false)
    private String productName;

    @Column(nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(nullable = false, updatable = false)
    private Instant receivedAt;

    private Instant processedAt;

    protected ProcessedOrderEvent() {
    }

    public ProcessedOrderEvent(String eventId, Long orderId, String sku, String productName, int quantity) {
        this.eventId = eventId;
        this.orderId = orderId;
        this.sku = sku;
        this.productName = productName;
        this.quantity = quantity;
        this.status = Status.PENDING;
        this.receivedAt = Instant.now();
    }

    public String getEventId() { return eventId; }
    public Long getOrderId() { return orderId; }
    public String getSku() { return sku; }
    public String getProductName() { return productName; }
    public int getQuantity() { return quantity; }
    public Status getStatus() { return status; }
    public Instant getReceivedAt() { return receivedAt; }
    public Instant getProcessedAt() { return processedAt; }

    public void markDone() {
        this.status = Status.DONE;
        this.processedAt = Instant.now();
    }
}
