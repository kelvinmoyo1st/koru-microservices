package com.koru.order.messaging;

import com.koru.order.domain.OutboxEvent;
import com.koru.order.event.OrderPlacedEvent;
import com.koru.order.repository.OutboxEventRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class OutboxRelay {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate;

    public OutboxRelay(OutboxEventRepository outboxEventRepository,
                        KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 2000)
    public void relayPendingEvents() {
        List<OutboxEvent> pending =
                outboxEventRepository.findTop50ByPublishedAtIsNullOrderByCreatedAtAsc();

        for (OutboxEvent outboxEvent : pending) {
            publishAndMark(outboxEvent);
        }
    }

    private void publishAndMark(OutboxEvent outboxEvent) {
        try {
            OrderPlacedEvent event = new OrderPlacedEvent(
                    outboxEvent.getEventId(),
                    outboxEvent.getOrderId(),
                    outboxEvent.getSku(),
                    outboxEvent.getProductName(),
                    outboxEvent.getQuantity(),
                    outboxEvent.getPlacedAt()
            );

            kafkaTemplate.send(outboxEvent.getTopic(), outboxEvent.getSku(), event)
                    .get(5, TimeUnit.SECONDS);

            outboxEvent.markPublished();
            outboxEventRepository.save(outboxEvent);

        } catch (Exception e) {
            // Left unpublished on purpose - publishedAt stays null,
            // so the next scheduled run picks this row up and retries it.
        }
    }
}
