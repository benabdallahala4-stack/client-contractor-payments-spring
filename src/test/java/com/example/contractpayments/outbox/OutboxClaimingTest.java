package com.example.contractpayments.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.contractpayments.outbox.application.OutboxStore;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class OutboxClaimingTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired OutboxStore store;

    @BeforeEach void clean() { jdbc.execute("TRUNCATE outbox_events, payments, jobs, contracts, profiles CASCADE"); }

    @Test void parallelWorkersClaimDisjointRowsAndExpiredLeaseIsRecoverable() throws Exception {
        Instant now = Instant.parse("2026-09-23T00:00:00Z");
        for (int i = 0; i < 4; i++) insert(now.minusSeconds(i));
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> { start.await(); return store.claimBatch(now, 2, Duration.ofSeconds(30)); });
            var second = pool.submit(() -> { start.await(); return store.claimBatch(now, 2, Duration.ofSeconds(30)); });
            start.countDown();
            var ids = new HashSet<UUID>();
            first.get().forEach(message -> ids.add(message.eventId()));
            second.get().forEach(message -> ids.add(message.eventId()));
            assertThat(ids).hasSize(4);
        }
        assertThat(store.claimBatch(now.plusSeconds(10), 10, Duration.ofSeconds(30))).isEmpty();
        assertThat(store.claimBatch(now.plusSeconds(31), 10, Duration.ofSeconds(30))).hasSize(4);
    }

    private void insert(Instant availableAt) {
        UUID id = UUID.randomUUID(), job = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO outbox_events
              (id, aggregate_type, aggregate_id, event_type, event_version, payload, status,
               attempt_count, available_at, created_at)
            VALUES (?, 'Job', ?, 'JobPaid', 1, '{}'::jsonb, 'PENDING', 0, ?, ?)
            """, id, job, java.sql.Timestamp.from(availableAt), java.sql.Timestamp.from(availableAt));
    }
}
