package com.example.contractpayments.outbox.application;

@FunctionalInterface
public interface EventPublisher {
    void publish(OutboxMessage message);
}
