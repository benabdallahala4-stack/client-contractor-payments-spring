package com.example.contractpayments.outbox.application;

import java.util.UUID;

public record OutboxMessage(UUID eventId, UUID aggregateId, String payload, int attemptCount) { }
