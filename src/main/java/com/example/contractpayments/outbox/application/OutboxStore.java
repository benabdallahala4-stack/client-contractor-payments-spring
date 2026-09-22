package com.example.contractpayments.outbox.application;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxStore {
    List<OutboxMessage> claimBatch(Instant now, int size, Duration lease);
    void markPublished(UUID eventId, Instant now);
    void reschedule(UUID eventId, Instant availableAt, String error);
}
