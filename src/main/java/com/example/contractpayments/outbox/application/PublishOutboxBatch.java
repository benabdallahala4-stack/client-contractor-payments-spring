package com.example.contractpayments.outbox.application;

import java.time.Duration;
import java.time.Instant;

public final class PublishOutboxBatch {
    private final OutboxStore store;
    private final EventPublisher publisher;
    private final Duration baseDelay;
    private final Duration maximumDelay;
    private final OutboxObserver observer;

    public PublishOutboxBatch(OutboxStore store, EventPublisher publisher,
            Duration baseDelay, Duration maximumDelay) {
        this(store, publisher, baseDelay, maximumDelay, OutboxObserver.NOOP);
    }

    public PublishOutboxBatch(OutboxStore store, EventPublisher publisher,
            Duration baseDelay, Duration maximumDelay, OutboxObserver observer) {
        this.store = store;
        this.publisher = publisher;
        this.baseDelay = baseDelay;
        this.maximumDelay = maximumDelay;
        this.observer = observer;
    }

    public int run(Instant now, int batchSize) {
        var messages = store.claimBatch(now, batchSize, Duration.ofSeconds(30));
        for (OutboxMessage message : messages) {
            try {
                publisher.publish(message);
                store.markPublished(message.eventId(), Instant.now());
                observer.published();
            } catch (RuntimeException error) {
                Duration delay = retryDelay(message.attemptCount());
                String detail = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
                store.reschedule(message.eventId(), now.plus(delay), detail.substring(0, Math.min(500, detail.length())));
                observer.failed();
            }
        }
        return messages.size();
    }

    private Duration retryDelay(int attempt) {
        int shift = Math.max(0, Math.min(20, attempt - 1));
        long seconds = Math.min(maximumDelay.toSeconds(), baseDelay.toSeconds() * (1L << shift));
        return Duration.ofSeconds(seconds);
    }
}
