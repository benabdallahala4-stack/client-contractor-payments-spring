package com.example.contractpayments.payment.domain;

import java.time.Instant;
import java.util.UUID;

public record JobPaidEvent(UUID eventId, Instant occurredAt, UUID jobId, UUID contractId,
        UUID paymentId, UUID clientId, UUID contractorId, int amountCents) {
    public String eventType() { return "JobPaid"; }
    public int eventVersion() { return 1; }
}
