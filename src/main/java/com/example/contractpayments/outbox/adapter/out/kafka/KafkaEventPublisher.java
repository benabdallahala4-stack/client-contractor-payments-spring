package com.example.contractpayments.outbox.adapter.out.kafka;

import com.example.contractpayments.configuration.OutboxProperties;
import com.example.contractpayments.outbox.application.EventPublisher;
import com.example.contractpayments.outbox.application.OutboxMessage;
import java.util.concurrent.TimeUnit;
import org.springframework.kafka.core.KafkaTemplate;

public final class KafkaEventPublisher implements EventPublisher {
    private final KafkaTemplate<String, String> kafka;
    private final String topic;

    public KafkaEventPublisher(KafkaTemplate<String, String> kafka, OutboxProperties properties) {
        this.kafka = kafka;
        this.topic = properties.topic();
    }

    @Override public void publish(OutboxMessage message) {
        try {
            kafka.send(topic, message.aggregateId().toString(), message.payload()).get(10, TimeUnit.SECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Kafka did not acknowledge event " + message.eventId(), error);
        } catch (Exception error) {
            throw new IllegalStateException("Kafka did not acknowledge event " + message.eventId(), error);
        }
    }
}
