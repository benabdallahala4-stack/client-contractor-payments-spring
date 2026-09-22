package com.example.contractpayments.payment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentRulesTest {
    @Test void validatesIdempotencyKeys() {
        assertThat(new IdempotencyKey("pay_job:42").value()).isEqualTo("pay_job:42");
        assertThatThrownBy(() -> new IdempotencyKey("bad key"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IdempotencyKey("x".repeat(129)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void createsStableVersionedEventFromPayment() {
        UUID eventId = UUID.randomUUID(), paymentId = UUID.randomUUID(), jobId = UUID.randomUUID();
        JobPaidEvent event = new JobPaidEvent(eventId, Instant.parse("2026-09-23T00:00:00Z"), jobId,
            UUID.randomUUID(), paymentId, UUID.randomUUID(), UUID.randomUUID(), 30000);
        assertThat(event.eventType()).isEqualTo("JobPaid");
        assertThat(event.eventVersion()).isEqualTo(1);
        assertThat(event.eventId()).isEqualTo(eventId);
    }
}
