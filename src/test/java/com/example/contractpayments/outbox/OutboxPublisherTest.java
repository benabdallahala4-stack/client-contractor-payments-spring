package com.example.contractpayments.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.contractpayments.outbox.application.EventPublisher;
import com.example.contractpayments.outbox.application.OutboxMessage;
import com.example.contractpayments.outbox.application.OutboxStore;
import com.example.contractpayments.outbox.application.PublishOutboxBatch;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OutboxPublisherTest {
    @Test void acknowledgesSuccessAndReschedulesFailureWithoutLeakingLongErrors() {
        Instant now = Instant.parse("2026-09-23T00:00:00Z");
        FakeStore store = new FakeStore(List.of(message(1), message(2)));
        EventPublisher publisher = message -> { if (message.attemptCount() == 2) throw new RuntimeException("x".repeat(900)); };
        new PublishOutboxBatch(store, publisher, Duration.ofSeconds(5), Duration.ofMinutes(5)).run(now, 10);
        assertThat(store.published).hasSize(1);
        assertThat(store.failed).hasSize(1);
        assertThat(store.failed.getFirst().error()).hasSize(500);
        assertThat(store.failed.getFirst().availableAt()).isAfter(now);
    }

    private OutboxMessage message(int attempts) {
        return new OutboxMessage(UUID.randomUUID(), UUID.randomUUID(), "{}", attempts);
    }

    private static final class FakeStore implements OutboxStore {
        private final List<OutboxMessage> claimed;
        private final List<UUID> published = new ArrayList<>();
        private final List<Failure> failed = new ArrayList<>();
        private FakeStore(List<OutboxMessage> claimed) { this.claimed = claimed; }
        @Override public List<OutboxMessage> claimBatch(Instant now, int size, Duration lease) { return claimed; }
        @Override public void markPublished(UUID id, Instant now) { published.add(id); }
        @Override public void reschedule(UUID id, Instant availableAt, String error) { failed.add(new Failure(id, availableAt, error)); }
        private record Failure(UUID id, Instant availableAt, String error) { }
    }
}
