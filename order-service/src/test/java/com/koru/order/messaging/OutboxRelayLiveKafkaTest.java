package com.koru.order.messaging;

import com.koru.order.domain.Order;
import com.koru.order.domain.Product;
import com.koru.order.event.OrderPlacedEvent;
import com.koru.order.repository.ProductRepository;
import com.koru.order.service.OrderService;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class OutboxRelayLiveKafkaTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OutboxRelay outboxRelay;

    @Test
    void relayActuallyPublishesToRealKafkaBroker() {
        Product product = productRepository.saveAndFlush(
                new Product("Koru 65% Keyboard", "KORU-65-01", 10999, 5));

        Order placed = orderService.placeOrder(product.getSku(), 1);

        outboxRelay.relayPendingEvents();

        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "live-test-" + UUID.randomUUID());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JacksonJsonDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(JacksonJsonDeserializer.TRUSTED_PACKAGES, "*");
        props.put(JacksonJsonDeserializer.VALUE_DEFAULT_TYPE, OrderPlacedEvent.class.getName());

        List<ConsumerRecord<String, OrderPlacedEvent>> collected = new ArrayList<>();

        try (KafkaConsumer<String, OrderPlacedEvent> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of("order.placed"));

            long deadline = System.currentTimeMillis() + 15000;
            while (System.currentTimeMillis() < deadline && collected.isEmpty()) {
                ConsumerRecords<String, OrderPlacedEvent> records = consumer.poll(Duration.ofSeconds(2));
                records.forEach(collected::add);
            }
        }

        boolean found = collected.stream()
                .anyMatch(record -> record.value().orderId().equals(placed.getId()));

        assertTrue(found, "Expected OrderPlacedEvent for order " + placed.getId() + " on the real topic");
    }
}
