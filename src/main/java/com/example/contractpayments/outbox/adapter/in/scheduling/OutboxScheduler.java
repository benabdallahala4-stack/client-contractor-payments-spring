package com.example.contractpayments.outbox.adapter.in.scheduling;

import com.example.contractpayments.configuration.OutboxProperties;
import com.example.contractpayments.outbox.application.PublishOutboxBatch;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

@Component
public class OutboxScheduler {
    private final PublishOutboxBatch publisher;
    private final OutboxProperties properties;
    private final ThreadPoolTaskExecutor executor;

    public OutboxScheduler(PublishOutboxBatch publisher, OutboxProperties properties,
            @Qualifier("outboxExecutor") ThreadPoolTaskExecutor executor) {
        this.publisher = publisher; this.properties = properties; this.executor = executor;
    }

    @Scheduled(fixedDelayString = "${payments.outbox.poll-delay:1s}")
    public void poll() {
        if (properties.enabled() && executor.getActiveCount() < properties.workerCount()) {
            executor.execute(() -> publisher.run(Instant.now(), properties.batchSize()));
        }
    }
}
