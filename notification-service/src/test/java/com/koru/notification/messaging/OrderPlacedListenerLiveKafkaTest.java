package com.koru.notification.messaging;

import com.koru.notification.domain.ProcessedOrderEvent;
import com.koru.notification.event.OrderPlacedEvent;
import com.koru.notification.repository.ProcessedOrderEventRepository;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

import java.time.Instant;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class OrderPlacedListenerLiveKafkaTest {

    @Autowired
    private KafkaListenerEndpointRegistry listenerRegistry;

    @Autowired
    private ProcessedOrderEventRepository processedOrderEventRepository;

    @Test
    void consumesRealMessageAndIgnoresReplayedDuplicate() throws InterruptedException {
        listenerRegistry.start();
        Thread.sleep(2000); // let the container join the consumer group before publishing

        String eventId = "evt-live-" + UUID.randomUUID();
        OrderPlacedEvent event = new OrderPlacedEvent(
                eventId, 555L, "KORU-TKL-01", "Koru TKL Keyboard", 1, Instant.now());

        publish(event);
        ProcessedOrderEvent firstPass = waitForDone(eventId);

        // Simulate Kafka's at-least-once redelivery of the exact same event.
        publish(event);
        Thread.sleep(3000);

        ProcessedOrderEvent afterReplay = processedOrderEventRepository.findById(eventId).orElseThrow();
        assertEquals(firstPass.getProcessedAt(), afterReplay.getProcessedAt(),
                "processedAt changing would mean the duplicate was reprocessed");

        listenerRegistry.stop();
    }

    private void publish(OrderPlacedEvent event) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JacksonJsonSerializer.class);

        try (KafkaProducer<String, OrderPlacedEvent> producer = new KafkaProducer<>(props)) {
            producer.send(new ProducerRecord<>("order.placed", event.sku(), event)).get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private ProcessedOrderEvent waitForDone(String eventId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < deadline) {
            Optional<ProcessedOrderEvent> found = processedOrderEventRepository.findById(eventId);
            if (found.isPresent() && found.get().getStatus() == ProcessedOrderEvent.Status.DONE) {
                return found.get();
            }
            Thread.sleep(500);
        }
        throw new AssertionError("Event was not processed within timeout: " + eventId);
    }
}
